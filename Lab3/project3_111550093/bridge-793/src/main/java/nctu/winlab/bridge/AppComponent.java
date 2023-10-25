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
package nctu.winlab.bridge;
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
// Injecting flow rules into the environment
import org.onosproject.net.flow.FlowRuleService;
// Selector Entries
// import org.onosproject.net.flow.TrafficSelector;    // Abstraction of a slice of network traffic
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
// Adding Flow Rule
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleOperations;
import org.onosproject.net.flow.DefaultFlowRule;
// FlowObjective Service
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;

// Processing Packet Service
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketPriority;

// information used in API
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;

import java.util.Map; // use on building MacTable
import java.util.Optional; // use to specify if it is nullable

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
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    private int flowTimeout = 10;
    private int flowPriority = 30;
    private ApplicationId appId;
    private BridgeProcessor bridgeProcessor = new BridgeProcessor();
    protected Map< DeviceId, Map<MacAddress, PortNumber> > forwardingTable = Maps.newConcurrentMap();
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.bridge"); // register app
        packetService.addProcessor(bridgeProcessor, PacketProcessor.director(3)); // add processor
        requestIntercepts(); // request packet in via packet service
	log.info("Started Learining Bridge.");
    }

    @Deactivate
    protected void deactivate() {
        flowRuleService.removeFlowRulesById(appId); // remove all flows installed by this app
        packetService.removeProcessor(bridgeProcessor); // remove the processor
        withdrawIntercepts(); // withdraw all request for packet in via packet service
        log.info("Stopped Learning Bridge.");
    }

    
    
    // Request ARP and ICMP packet in via packet service.
    private void requestIntercepts() 
    {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);

        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }
    
    // withdraw all request for packet in via packet service
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    

    private class BridgeProcessor implements PacketProcessor
    {
        @Override
        public void process(PacketContext context) {
            if ( context.isHandled() ) return; // stop when meeeting handled packets 
            if ( context.inPacket().parsed().getEtherType() != Ethernet.TYPE_IPV4 &&
                 context.inPacket().parsed().getEtherType() != Ethernet.TYPE_ARP )   return;

            // basic info of packet
            InboundPacket pkt = context.inPacket();
            Ethernet  ethPkt = pkt.parsed();
            ConnectPoint cp = pkt.receivedFrom();
            MacAddress srcMac = ethPkt.getSourceMAC(),
                       dstMac = ethPkt.getDestinationMAC();
            DeviceId hostID = cp.deviceId();
            PortNumber inPort = cp.port();    

            // if the device has not been recorded, record it
            forwardingTable.putIfAbsent(hostID, Maps.newConcurrentMap());
            
            // get the forwarding table of the device
            Map<MacAddress, PortNumber> macTable = forwardingTable.get(hostID);            
            PortNumber outPort = macTable.get(dstMac);

            // record the source mac address and port
            macTable.put(srcMac, inPort); 
            log.info("Add an entry to the port table of `{}`. MAC address: `{}` => Port: `{}`.", 
                hostID, srcMac.toString(), inPort.toString());

            if(outPort == null) {
                // if the destination mac address is not recorded, flood
                flood(context);
		log.info("MAC address `{}` is missed on `{}`. Flood the packet.",
                    dstMac.toString(), hostID.toString());
            }
            else {
                // if the destination mac address is recorded, forward and install flow rule
                packetOut(context, outPort);
                installRule(context, srcMac, dstMac, outPort, hostID);
            }
        }

        private void installRule(PacketContext context, MacAddress src, MacAddress dst, PortNumber outPort, DeviceId hostID)
        {
            // set match field and action
            TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
            TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
            selectorBuilder.matchEthSrc(src).matchEthDst(dst);
            treatmentBuilder.setOutput(outPort);

            // set flow rule
            ForwardingObjective flowRule = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatmentBuilder.build())
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(flowTimeout)
                .add();
            
            flowObjectiveService.forward(hostID, flowRule);
            log.info("MAC address `{}` is matched on `{}`. Install a flow rule.", 
                    dst.toString(), hostID.toString()); 
        }

        // flood the packet
        private void flood(PacketContext context)
        {
            packetOut(context, PortNumber.FLOOD);
        }

        // send the packet to the specified port
        private void packetOut(PacketContext context, PortNumber outPort)
        {
            context.treatmentBuilder().setOutput(outPort);
            context.send();   
        }
    }
}
