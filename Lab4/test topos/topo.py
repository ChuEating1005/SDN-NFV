#!/usr/bin/python3

from mininet.topo import Topo
from mininet.net import Mininet
from mininet.log import setLogLevel
from mininet.node import RemoteController
from mininet.cli import CLI
from mininet.link import TCLink
import os

class MyTopo( Topo ):

    def __init__( self ):
        Topo.__init__( self, sopts={ 'protocols': 'OpenFlow14', 'switch': 'ovs' } )
        # Add hosts
        h1 = self.addHost('h1', ip='0.0.0.0', mac='ea:e9:78:fb:fd:01')
        h2 = self.addHost('h2', ip='0.0.0.0', mac='ea:e9:78:fb:fd:02')
        h3 = self.addHost('h3', ip='0.0.0.0', mac='ea:e9:78:fb:fd:03')
        h4 = self.addHost('h4', ip='0.0.0.0', mac='ea:e9:78:fb:fd:04')
        dhcp = self.addHost('dhcp', ip='10.1.11.3/24', mac='ea:e9:78:fb:fd:05')
        # Add switches
        s1 = self.addSwitch('s1')
        s2 = self.addSwitch('s2')
        s3 = self.addSwitch('s3')
        s4 = self.addSwitch('s4')
        s5 = self.addSwitch('s5')
        s6 = self.addSwitch('s6')
        # Add links                                         
        self.addLink(s1, s2)      
        self.addLink(s2, s3)
        self.addLink(s2, s5)
        self.addLink(s4, s5)
        self.addLink(s5, s6)
        self.addLink(s1, h1)
        self.addLink(s3, h2)
        self.addLink(s4, h3)
        self.addLink(s6, h4)
        self.addLink(s6, dhcp)

def run():
    topo = MyTopo()
    controller = RemoteController(name='c0', ip='127.0.0.1', port=6653)
    net = Mininet(topo=topo, controller=controller, link=TCLink)

    net.start()

    fileDir = os.path.realpath(os.path.dirname(__file__))
    dhcpdConfigPath = fileDir + '/dhcpd.conf'

    print("[+] Run DHCP server")
    dhcp = net.getNodeByName('dhcp')
    pidFile = '/run/dhcp-server-dhcpd.pid'
    dhcp.cmdPrint('/usr/sbin/dhcpd 4 -pf %s -cf %s %s' % (pidFile, dhcpdConfigPath, dhcp.defaultIntf()))

    CLI(net)
    print("[-] Killing DHCP server")
    dhcp.cmdPrint("kill -9 `cat %s`" % pidFile)
    net.stop()

if __name__ == '__main__':
    setLogLevel('info')
    run()
