main:
	docker compose up -d
	sudo ovs-vsctl add-br ovsbr
	sudo ovs-vsctl set bridge ovsbr protocols=OpenFlow14
	sudo ovs-vsctl set-controller ovsbr tcp:127.0.0.1:6653
	sudo ovs-docker add-port ovsbr eth1 R1 --ipaddress=172.20.0.2/16
	sudo ovs-docker add-port ovsbr eth1 R2 --ipaddress=172.20.0.3/16
	sudo ovs-docker add-port ovsbr eth1 R3 --ipaddress=172.20.0.4/16
clean:
	docker compose down
	sudo ovs-vsctl del-br ovsbr