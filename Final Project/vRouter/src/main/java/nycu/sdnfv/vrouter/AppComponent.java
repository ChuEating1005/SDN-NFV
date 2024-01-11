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
package nycu.sdnfv.vrouter;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onlab.packet.Ethernet;
import org.onlab.packet.ARP;
import org.onlab.packet.MacAddress;
import org.onlab.packet.IPv4;
import org.onlab.packet.TCP;
import org.onlab.packet.IpAddress;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.Ip4Prefix;

import org.onosproject.net.host.InterfaceIpAddress;

import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.FilteredConnectPoint;

import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficSelector;

import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.net.intent.IntentService;

import org.onosproject.net.intf.Interface;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;

// import org.onosproject.routeservice;
// import org.onosproject.routeservice.RouteService;
import org.onosproject.routeservice.*;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;

import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;

import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = AppComponent.class
)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final vRouterConfigListener vRouterListener = new vRouterConfigListener();
    private final ConfigFactory<ApplicationId, vRouterConfig> factory = new ConfigFactory<ApplicationId, vRouterConfig>(
        APP_SUBJECT_FACTORY, vRouterConfig.class, "router") {
        @Override
        public vRouterConfig createConfig() {
            return new vRouterConfig();
        }
    };

   @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

     @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected InterfaceService intfService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected RouteService routeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    ApplicationId appId;
    ConnectPoint quaggaCP;
    MacAddress quaggaMAC;
    MacAddress virtualMAC;
    IpAddress virtualIP;
    ArrayList<IpAddress> peers = new ArrayList<IpAddress>();
    ArrayList<ConnectPoint> peerCPs = new ArrayList<ConnectPoint>();

    private vRouterPacketProcessor vRouterProcessor = new vRouterPacketProcessor();

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nycu.sdnfv.vrouter");
        packetService.addProcessor(vRouterProcessor, PacketProcessor.director(6));
        cfgService.addListener(vRouterListener);
        cfgService.registerConfigFactory(factory);
        requestPacketsIn();
        log.info("Started: " + appId.name());
    }

    @Deactivate
    protected void deactivate() {
        cancelPacketsIn();
        cfgService.removeListener(vRouterListener);
        cfgService.unregisterConfigFactory(factory);
        packetService.removeProcessor(vRouterProcessor);
        vRouterProcessor = null;
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        requestPacketsIn();
    }

    private void requestPacketsIn() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    private void cancelPacketsIn() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

     private class vRouterConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {   
            /* read config file */
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED) && event.configClass().equals(vRouterConfig.class)) {
                vRouterConfig config = cfgService.getConfig(appId, vRouterConfig.class);
                if (config != null) {
                    quaggaCP = config.getQuaggaCP();
                    quaggaMAC = config.getQuaggaMAC();
                    virtualMAC = config.getVirtualMAC();
                    virtualIP = config.getVirtualIP();
                    peers = config.getPeers();
                    
                    /* Log Info */
                    log.info("Quagga is connected to: " + quaggaCP);
                    log.info("Quagga-mac: " + quaggaMAC);
                    log.info("Virtual-mac: " + virtualMAC);
                    log.info("Virtual-ip: " + virtualIP);

                    for (int i = 0; i < peers.size(); i++) {
                        log.info("Peer: " + peers.get(i));
                    }

                    /* for each peer, install two intents. 1 for peer -> quagga, 2 for quagga -> peer */
                    for (IpAddress peerIp : peers) {
                        Interface peerIntf = intfService.getMatchingInterface(peerIp);
                        ConnectPoint peerCP = peerIntf.connectPoint();
                        peerCPs.add(peerCP);
                        
                        /* peer --> quagga */
                        IpAddress speakerIp = peerIntf.ipAddressesList().get(0).ipAddress();
                        bgpIntent(peerCP, quaggaCP, speakerIp);

                        /* peer <-- quagga */
                        bgpIntent(quaggaCP, peerCP, peerIp);
                    }
                }
            }
        }

        private void bgpIntent(ConnectPoint ingress, ConnectPoint egress, IpAddress dstIp) {
            /* install intent for BGP packet */
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPDst(dstIp.toIpPrefix());

            TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();

            FilteredConnectPoint ingressPoint = new FilteredConnectPoint(ingress);
            FilteredConnectPoint egressPoint = new FilteredConnectPoint(egress);

            log.info("[BGP] `" + ingress + " => " + egress + " is submitted.");

            PointToPointIntent intent = PointToPointIntent.builder()
                        .filteredIngressPoint(ingressPoint)
                        .filteredEgressPoint(egressPoint)
                        .selector(selector.build())
                        .treatment(treatment)
                        .priority(40)
                        .appId(appId)
                        .build();
            intentService.submit(intent);
        }
    }


    private class vRouterPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            if (context.isHandled() || context.inPacket().parsed().getEtherType() == Ethernet.TYPE_ARP) {
                return;
            }

            log.info("=== In vRouterPacketProcessor ===");

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            MacAddress dstMAC = ethPkt.getDestinationMAC();
            ConnectPoint srcCP = pkt.receivedFrom();

            IPv4 ipv4Pkt = (IPv4) ethPkt.getPayload();
            IpAddress dstIp = IpAddress.valueOf(ipv4Pkt.getDestinationAddress());

            log.info("DstIp: " + dstIp);
            log.info("DstMac: " + dstMAC);

            if (ipv4Pkt.getProtocol() == IPv4.PROTOCOL_TCP) {
                TCP tcpPkt = (TCP) ipv4Pkt.getPayload();
                if (tcpPkt.getDestinationPort() == 179) {
                    log.info("[*] Get PGB Packet");
                    return;
                }
            }

            /* 先判斷 packet 是從裡面出去，還是從外面進來的 */
            if (dstMAC.equals(virtualMAC)) {             // packet 從裡面要出去的
                /* 那就從 RouteService 去找路徑，看要把 packet 往哪邊送 */
                ResolvedRoute bestRoute = getBestRoute(dstIp);
                if (bestRoute != null) {            // 有找到路徑 (知道怎麼走)
                    // IpPrefix dstPrefix = bestRoute.prefix();
                    IpAddress nextHopIp = bestRoute.nextHop();
                    MacAddress nextHopMac = bestRoute.nextHopMac();

                    Interface outIntf = intfService.getMatchingInterface(nextHopIp);
                    ConnectPoint outCP = outIntf.connectPoint();

                    sdn_external_Intent(context, srcCP, outCP, quaggaMAC, nextHopMac);
                    context.block();
                }
            }
            else if (dstMAC.equals(quaggaMAC)) {         // packet 從外面進來的
                /* 接著判斷是: 外往外，外往內 */
                Set<Host> dstHost = hostService.getHostsByIp(dstIp);
                if (dstHost.size() > 0) {           // 有找到 host，代表是外往內
                    Host host = new ArrayList<Host>(dstHost).get(0);
                    ConnectPoint hostCP = ConnectPoint.fromString(host.location().toString());
                    MacAddress hostMAC = host.mac();

                    sdn_external_Intent(context, srcCP, hostCP, virtualMAC, hostMAC);
                    context.block();
                }
                else {                              // 找不到 host有兩種情況: 1.host 不存在，2. host在其他網段
                    /* 就用 RouteService 去看，有沒有到達 host network 的路徑 */
                    ResolvedRoute bestRoute = getBestRoute(dstIp);
                    if (bestRoute != null) {        // 有找到路徑
                        IpAddress nextHopIp = bestRoute.nextHop();
                        MacAddress nextHopMAC = bestRoute.nextHopMac();

                        Interface outIntf = intfService.getMatchingInterface(nextHopIp);
                        ConnectPoint outCP = outIntf.connectPoint();

                        external_external_intent(context, outCP, quaggaMAC, nextHopMAC);
                        context.block();
                    }
                }
            }
        }

        private ResolvedRoute getBestRoute(IpAddress targetIp) {
            Collection<RouteTableId> routingTable = routeService.getRouteTables();
            for (RouteTableId tableID : routingTable) {
                for (RouteInfo info : routeService.getRoutes(tableID)) {
                    ResolvedRoute bestRoute = info.bestRoute().get();

                    IpPrefix dstPrefix = bestRoute.prefix();                /* 要到的 Ip Network */
                    if (dstPrefix.contains(targetIp)) {
                        return bestRoute;
                    }
                }
            }
            return null;
        }
    }

    private void sdn_external_Intent(PacketContext context, ConnectPoint ingress, ConnectPoint egress, MacAddress srcMac, MacAddress dstMac) {
        IPv4 ipv4Pkt = (IPv4) context.inPacket().parsed().getPayload();
        IpAddress dstIp = IpAddress.valueOf(ipv4Pkt.getDestinationAddress());
        // Ip4Prefix ip4Prefix = Ip4Prefix.valueOf(ipv4Pkt.getDestinationAddress(), Ip4Prefix.MAX_MASK_LENGTH);

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPDst(dstIp.toIpPrefix());

        /* 要把 src MAC 跟 dst MAC 換掉 */
        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                    .setEthSrc(srcMac)
                    .setEthDst(dstMac);

        FilteredConnectPoint ingressPoint = new FilteredConnectPoint(ingress);
        FilteredConnectPoint egressPoint = new FilteredConnectPoint(egress);

        log.info("[SDN_External] " + ingress + " => " + egress + " is submitted.");

        PointToPointIntent intent = PointToPointIntent.builder()
                    .filteredIngressPoint(ingressPoint)
                    .filteredEgressPoint(egressPoint)
                    .selector(selector.build())
                    .treatment(treatment.build())
                    .priority(50)
                    .appId(appId)
                    .build();
        intentService.submit(intent);
    }

    private void external_external_intent(PacketContext context, ConnectPoint egress, MacAddress srcMac, MacAddress dstMac) {
        IPv4 ipv4Pkt = (IPv4) context.inPacket().parsed().getPayload();
        IpAddress dstIp = IpAddress.valueOf(ipv4Pkt.getDestinationAddress());
        IpPrefix ip4Prefix = routeService.longestPrefixLookup(dstIp).get().prefix();

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPDst(ip4Prefix);

        /* 要把 src MAC 跟 dst MAC 換掉 */
        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                    .setEthSrc(srcMac)
                    .setEthDst(dstMac);

        Set<FilteredConnectPoint> ingressPoints = new HashSet<FilteredConnectPoint>();
        FilteredConnectPoint egressPoint = new FilteredConnectPoint(egress);
        for (ConnectPoint cp : peerCPs) {
            if (!cp.equals(egress)) {
                ingressPoints.add(new FilteredConnectPoint(cp));
                log.info("[External_External] " + cp + " => " + egress + " is submitted.");
            }
        }

        MultiPointToSinglePointIntent intent = MultiPointToSinglePointIntent.builder()
                    .filteredIngressPoints(ingressPoints)
                    .filteredEgressPoint(egressPoint)
                    .selector(selector.build())
                    .treatment(treatment.build())
                    .priority(60)
                    .appId(appId)
                    .build();
        intentService.submit(intent);
    }
}