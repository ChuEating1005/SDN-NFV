from mininet.topo import Topo

class MyTopo( Topo ):
    def __init__(self):
        Topo.__init__(self)
       
        # Add hosts
        h1 = self.addHost('h1')
        h2 = self.addHost('h2')
        h3 = self.addHost('h3')


        # Add switches
        s1 = self.addSwitch('s1')
        s2 = self.addSwitch('s2')
        s3 = self.addSwitch('s3')


        # Add links
        self.addLink(s1, s2)
        self.addLink(s2, s3)
        self.addLink(s1, s3)
        self.addLink(s1, h1)
        self.addLink(s2, h2)
        self.addLink(s3, h3)
        
topos = { 'topo_111550093':  MyTopo }

