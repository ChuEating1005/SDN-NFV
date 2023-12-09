/*
 * Copyright 2023-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nctu.winlab.ProxyArp;
import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
// OSGI Service Annotation
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Dictionary;
import java.util.Properties;
import static org.onlab.util.Tools.get;

/* Import Libs*/
import com.google.common.collect.Maps; //Provided ConcurrentMap Implementation
import org.onosproject.core.ApplicationId; // Application Identifier
import org.onosproject.core.CoreService; // Core Service

// Gain Information about existed flow rules & 

// Selector Entries
// import org.onosproject.net.flow.TrafficSelector;    // Abstraction of a slice of network traffic
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;

// Processing Packet Service
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketPriority;

//
import org.onosproject.net.edge.EdgePortService;

// information used in API
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.ARP;
import org.onlab.packet.TCP;
import org.onlab.packet.UDP;
import org.onlab.packet.MacAddress;
import org.onlab.packet.Ip4Address;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;

import java.util.Map; // use on building MacTable
import java.util.HashMap;
import java.util.Optional; // use to specify if it is nullable
import java.nio.ByteBuffer;
/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent{
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgePortService;


    private ApplicationId appId;
    private ProxyArpProcessor proxyArpProcessor = new ProxyArpProcessor();
    protected Map< Ip4Address, MacAddress> arpTable = new HashMap<>();
	protected Map< MacAddress, ConnectPoint> cpTable =  new HashMap<>();   
	private final Logger log = LoggerFactory.getLogger(getClass());
    
    @Activate
    protected void activate() {
    	appId = coreService.registerApplication("nctu.winlab.ProxyArp"); // register app
        packetService.addProcessor(proxyArpProcessor, PacketProcessor.director(3)); // add processor
		log.info("Started Proxy ARP."); 
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(proxyArpProcessor); // remove the processor
        log.info("Stopped Proxy ARP.");
	}
	

    private class ProxyArpProcessor implements PacketProcessor{
    	@Override
    	public void process(PacketContext context){
    	    if (context.isHandled()) return; // stop when meeeting handled packets 
            if (context.inPacket().parsed().getEtherType() != Ethernet.TYPE_ARP)   return;

            // basic info of packet
            InboundPacket pkt = context.inPacket();
			ConnectPoint inport = pkt.receivedFrom();
            Ethernet  etherFrame = pkt.parsed();
			ARP arpDatagram	= (ARP) etherFrame.getPayload();

			if (arpDatagram.getProtocolType() != ARP.PROTO_TYPE_IP) return;

			// get 4 headers in ARP_REQUEST message
			Ip4Address dstIP = Ip4Address.valueOf(arpDatagram.getTargetProtocolAddress());
			Ip4Address srcIP = Ip4Address.valueOf(arpDatagram.getSenderProtocolAddress());
			MacAddress dstMac = etherFrame.getDestinationMAC();
			MacAddress srcMac = etherFrame.getSourceMAC();

            // if the inport cp or source MacAddress has not been recorded, record it
            arpTable.putIfAbsent(srcIP, srcMac);
			cpTable.putIfAbsent(srcMac, inport);

			// get the outport cp and destination MacAddress from the tables
            MacAddress targetMac = arpTable.get(dstIP);
			ConnectPoint outport = cpTable.get(targetMac); 
           	
			if (arpDatagram.getOpCode() == ARP.OP_REQUEST)
			{
				if (targetMac == null) {
                	// if the destination mac address is not recorded, flood to edge ports
					log.info("TABLE MISS. Send request to edge ports");
					flood(etherFrame, inport);
				}
				else {
                	// if the destination mac address is recorded, packet-out ARP_REPLY to port which it came from
					log.info("TABLE HIT. Requested MAC = {}", targetMac.toString());
					Ethernet ethArpReply = ARP.buildArpReply(dstIP, targetMac, etherFrame);
                	packetOut(ethArpReply, inport);
				}
            }
			else if (arpDatagram.getOpCode() == ARP.OP_REPLY)
			{
				// received ARP_REPLY from destination host, packet-out to port which it came from
				log.info("RECV REPLY. Requested MAC = {}", srcMac.toString());
				packetOut(etherFrame, outport);
			}
    	}
    	private void flood(Ethernet etherFrame, ConnectPoint inport)
    	{
        	for(ConnectPoint cp : edgePortService.getEdgePoints())
			{
				if(cp.equals(inport))
					continue;
				packetOut(etherFrame, cp);
			}
    	}
    	private void packetOut(Ethernet etherFrame, ConnectPoint cp)
        {
            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(cp.port())
                .build();
            OutboundPacket out_pkt = new DefaultOutboundPacket(cp.deviceId(), treatment, ByteBuffer.wrap(etherFrame.serialize()));
            packetService.emit(out_pkt); 
        }
    }

}
