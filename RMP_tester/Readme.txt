-------  Firstly -----------

1) Make sure that you have the jxta libraries.
2) Open every *.bat (for windows) or *.sh (for linux) file and change the following :
	C:/JXTA/lib/jxta.jar		==>  
	C:/JXTA/lib/bcprov-jdk14.jar	==>  Replace everywhere the location {C:/JXTA/lib/} or {/home/username/JXTA/lib/} with the one that the jxta library resides on your system.
	C:/JXTA/lib/log4j.jar		==>  
3) Give rw rights 
	a) to the base directory of the tester 
	b) to the directory jxta_peer_settings
	c) to file RMP_TesterApp.class if you plan to recompile the tester.
	d) +x rights to the *.sh files for the linux OS.
4) Please be patient :
	a) With the timeouts :-)
	b) With the semi functional testing release of RMP, as in the following days are going to be uploaded a newer versions withmore features.


==== Compilation =====
Simply execute the script Compile.bat (for windows) or the Compile.sh (for linux).


==== Running the tester  =====
1) You have to be patient because the timeouts that are used are set for developement. Depending on your system or even your network connection,
you should assume that yous system has hang up after unleast 20seconds for each script that runs the instance of the tester.

2) Firstly you have to create a channel. Just use the InstanceA script and after the prompt appears on screen with the choices, type :
	0  and press enter.

3) Afterwards the following prompt appears :
        "Please give the ID of the channel :>"
        
   Type a name for the channel that you are creating, e.g. CNN and then press enter.
   
4) Wait for about 20 seconds (at most) until the following line apperas on the output of the console :
   
   	" This peer is not only a provider[local-root], it is THE root-sender.
	  Please wait for others to connect and then type the message you want to propagae reliably hitting enter at the end.
	  You have to wait for a message like the following to be printed:
	  Peer :"XXXXX" connected to us "
   
   Now the created the channel (CNN) is ready to accept join requests.
   
5) Use the InstanceB script and after the prompt appears on screen with the choices, type :
	1  and press enter.

6) Afterwards the following prompt appears :
        "Please give the ID of the channel :>"
        
   Type the name of the channel that you created in steps 1)-4) e.g. CNN and then press enter.
   
7) Wait for several seconds. Now the second instance of the tester tries to join the channel CNN.
   This part is probably the most slow. We have to wait until we see the message :
   "Peer :"RMP_nodeB" connected to us [we are a provider]"
   on the output of (!!!CAUTION!!!) of the FIRST INSTANCE of the tester (the one which created the channel CNN).
   There is always a set of XML messages displayed in the consoles of both tester-instrances, dont get lost among them :-)
   
8) You now can type in the console of the first instance any message that you want to be multicasted to anyone that is joined to the CNN channel.
   You can also (many2many) do the same from every console, type your message and then hit ENTER.

9) You can repeat steps 5)-8) by executing respectively te scripts for the InstanceC and InstanceD. Just be patient until the creator of the channel (InstanceA) see them also as connected.
   Then more than one peers will be joined to the channel and you can notice that all receive the sent messages by the channel creator (or else "root" of the multicast tree of the channel).
   
10)To "destroy" the channel and close every open with 1 move, type from the first console (root node) the word:
	close
   Wait about 20-30 seconds (I 've set big tiemouts... you can change them from source. Experiment some times to find the proper values for your net/system combination)

11)To leave the channel if the node is not the root type in the second, third,... console:
	leave
   Wait about 20-30 seconds (I 've set big tiemouts... you can change them from source. Experiment some times to find the proper values for your net/system combination)
   
   
 ATTENTION : 	The default timeouts of the app are quite big to prevent unexpected exceptions.
 		Most of the thrown Exceptions are not fatal but just informative (they help during developement). Just ignore them :-)
 		Between every action you do : start/stop a peer, send a message, join a channel, close a channel, disconnect or anything else... keep a reasonable time-gap.