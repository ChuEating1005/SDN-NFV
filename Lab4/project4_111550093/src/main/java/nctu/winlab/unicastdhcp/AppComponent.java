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
package nctu.winlab.unicastdhcp;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;

import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.PointToPointIntent;

import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;

import org.onosproject.cfg.ComponentConfigService;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Map;
import java.util.Properties;
import static org.onlab.util.Tools.get;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.IPv4;
import org.onlab.packet.UDP;
import org.onlab.packet.TpPort;


/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent{

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
	protected ComponentConfigService cfgService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY)
	protected NetworkConfigRegistry ncfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private ApplicationId appId;
    private final DhcpConfigListener cfgListener = new DhcpConfigListener();
    private final ConfigFactory<ApplicationId, DhcpConfig> factory = new ConfigFactory<ApplicationId, DhcpConfig>(APP_SUBJECT_FACTORY, DhcpConfig.class, "UnicastDhcpConfig") {
        @Override
        public DhcpConfig createConfig() {
            return new DhcpConfig();
        }
    };
    protected DeviceId DHCPvS;
	protected PortNumber DHCPvSP;
    private int flowPriority = 30;
    private DhcpProcessor dhcpProcessor = new DhcpProcessor();

    

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.unicastdhcp");
        ncfgService.addListener(cfgListener);
        ncfgService.registerConfigFactory(factory);
        packetService.addProcessor(dhcpProcessor, PacketProcessor.director(3)); // add processor
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        ncfgService.removeListener(cfgListener);
        ncfgService.unregisterConfigFactory(factory);
        packetService.removeProcessor(dhcpProcessor); 
        log.info("Stopped");
    }

    private class DhcpProcessor implements PacketProcessor
    {
        @Override
        public void process(PacketContext context) {
            if ( context.isHandled() ) return; // stop when meeeting handled packets 
            if ( context.inPacket().parsed().getEtherType() != Ethernet.TYPE_IPV4 )   return;

            // basic info of packet
            InboundPacket pkt = context.inPacket();
            ConnectPoint cp = pkt.receivedFrom();
            DeviceId hostID = cp.deviceId();
            PortNumber inPort = cp.port(); 

            PointToPointIntent intent;
            ConnectPoint cp1 = new ConnectPoint(DHCPvS, DHCPvSP);
            FilteredConnectPoint egressPoint = new FilteredConnectPoint(cp1);
            FilteredConnectPoint ingressPoint = new FilteredConnectPoint(cp);
            TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
            selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPProtocol(IPv4.PROTOCOL_UDP)
                    .matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
                    .matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT));
            intent = PointToPointIntent.builder()
                .appId(appId)
                .filteredIngressPoint(ingressPoint)
                .filteredEgressPoint(egressPoint)
                .selector(selectorBuilder.build())
                .priority(flowPriority)
                .build();
            intentService.submit(intent);
            log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.", hostID, inPort, DHCPvS, DHCPvSP);

            selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPProtocol(IPv4.PROTOCOL_UDP)
                    .matchUdpSrc(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
                    .matchUdpDst(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));
            intent = PointToPointIntent.builder()
                .appId(appId)
                .filteredIngressPoint(egressPoint)
                .filteredEgressPoint(ingressPoint)
                .selector(selectorBuilder.build())
                .priority(flowPriority)
                .build();
            intentService.submit(intent);
            log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.", DHCPvS, DHCPvSP, hostID, inPort);
        }
    }
    
    private class DhcpConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED) && event.configClass().equals(DhcpConfig.class)) {
                DhcpConfig config = ncfgService.getConfig(appId, DhcpConfig.class);
                if (config != null) {
                    String[] splitted = config.dhcp().split("/");
                    DHCPvS = DeviceId.deviceId(splitted[0]);
                    DHCPvSP = PortNumber.portNumber(splitted[1]);
                    requestIntercepts();
                    log.info("DHCP server is connected to `{}`, port `{}`", DHCPvS, DHCPvSP);
                }
            }
        }
        private void requestIntercepts() 
        {
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
            selector.matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_UDP)
                .matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
                .matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT));
            packetService.requestPackets(selector.build(),PacketPriority.REACTIVE, appId);
            selector.matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_UDP)
                .matchUdpSrc(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
                .matchUdpDst(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));
            packetService.requestPackets(selector.build(),PacketPriority.REACTIVE, appId);
        }
    }
}
