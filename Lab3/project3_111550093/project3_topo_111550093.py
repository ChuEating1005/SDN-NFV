from mininet.topo import Topo

class Project3_Topo_111550093( Topo ):
    def __init__(self):                   
        Topo.__init__(self)        
        # Add hosts
        h1 = self.addHost('h1')
        h2 = self.addHost('h2')
        h3 = self.addHost('h3')
        h4 = self.addHost('h4')
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
topos = { 'topo_111550093': Project3_Topo_111550093  }
