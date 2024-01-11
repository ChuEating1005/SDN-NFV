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

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onosproject.net.ConnectPoint;
import org.onlab.packet.MacAddress;
import org.onlab.packet.IpAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class vRouterConfig extends Config<ApplicationId> {
    public static final String QUAGGA_LOCATION = "quagga";
    public static final String QUAGGA_MAC = "quagga-mac";
    public static final String VIRTUAL_IP = "virtual-ip";
    public static final String VIRTUAL_MAC = "virtual-mac";
    public static final String PEERS = "peers";

    Function<String, String> func = (String e)-> {return e;};

    @Override
    public boolean isValid() {
        return hasFields(QUAGGA_LOCATION, QUAGGA_MAC, VIRTUAL_IP, VIRTUAL_MAC, PEERS);
    }

    public ConnectPoint getQuaggaCP() {
        return ConnectPoint.fromString(get(QUAGGA_LOCATION, null));
    }

    public MacAddress getQuaggaMAC() {
        return MacAddress.valueOf(get(QUAGGA_MAC, null));
    }

    public IpAddress getVirtualIP() {
        return IpAddress.valueOf(get(VIRTUAL_IP, null));
    }

    public MacAddress getVirtualMAC() {
        return MacAddress.valueOf(get(VIRTUAL_MAC, null));
    }

    public ArrayList<IpAddress> getPeers() {
        List<String> peers = getList(PEERS, func);
        ArrayList<IpAddress> peersIp = new ArrayList<IpAddress>();
       
        for (String peerIp : peers) {
            peersIp.add(IpAddress.valueOf(peerIp));
        }
       
        return peersIp;
    }
}