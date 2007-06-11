/*
 *  Copyright (c) 2006 Sun Microsystems, Inc.  All rights
 *  reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the
 *  distribution.
 *
 *  3. The end-user documentation included with the redistribution,
 *  if any, must include the following acknowledgment:
 *  "This product includes software developed by the
 *  Sun Microsystems, Inc. for Project JXTA."
 *  Alternately, this acknowledgment may appear in the software itself,
 *  if and wherever such third-party acknowledgments normally appear.
 *
 *  4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA" must
 *  not be used to endorse or promote products derived from this
 *  software without prior written permission. For written
 *  permission, please contact Project JXTA at http://www.jxta.org.
 *
 *  5. Products derived from this software may not be called "JXTA",
 *  nor may "JXTA" appear in their name, without prior written
 *  permission of Sun.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED.  IN NO EVENT SHALL SUN MICROSYSTEMS OR
 *  ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 *  USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 *  OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 *  SUCH DAMAGE.
 *  ====================================================================
 *
 *  This software consists of voluntary contributions made by many
 *  individuals on behalf of Project JXTA.  For more
 *  information on Project JXTA, please see
 *  <http://www.jxta.org/>.
 *
 *  This license is based on the BSD license adopted by the Apache Foundation.
 *
 *  $Id: RMP_TesterApp.java,v 1.3 2006/09/07 22:15:03 dimosp Exp $
 */



import java.io.*;
import java.net.*;
import javax.security.cert.*;

import net.jxta.exception.*;
import net.jxta.id.*;
import net.jxta.peergroup.*;
import net.jxta.platform.*;
import rmp.*;

public class RMP_TesterApp implements JxtaReliableEventListener {

    private final static int UNACCEPTABLE_CHOICE = -1001;

    private PeerGroup netPeerGroup = null;
    private String node_name = "";
    private String thePrincipal = "dimos-rmp";
    private String thePassword = "d1m0s-rmp";
    private NetworkConfigurator config = null;

    private boolean create_channel = false;
    private JxtaReliableMcastChannel theChannel = null;
    private JxtaReliableMcastService theRMPService = null;
    private String channelID = "";


    public RMP_TesterApp(boolean create_ch , String channel_id ) {
        node_name = "RMP_" + System.getProperty("NODE_NAME", ".cache");
        create_channel = create_ch;
        channelID = channel_id;
    }


    private void startJxta() {
        configure_jxta_node();
        try {
                // create and start the default JXTA NetPeerGroup
                NetPeerGroupFactory factory  = new NetPeerGroupFactory();
                netPeerGroup = factory.getInterface();
        }
        catch (PeerGroupException e) {
                // could not instantiate the group, print the stack and exit
                System.out.println("fatal error : group creation failure");
                e.printStackTrace();
                System.exit(1);
        }
}


private void configure_jxta_node()
{
    config = new NetworkConfigurator();
    File home = new File(System.getProperty("JXTA_HOME", ".cache") + "/" + node_name);
    config.setHome(home);
    if (!config.exists()) {

        config.setPeerID(IDFactory.newPeerID(PeerGroupID.defaultNetPeerGroupID));
        config.setName(this.node_name);
        config.setDescription("Testing peer for RMP");
        config.setMode(NetworkConfigurator.EDGE_NODE);
        config.setPrincipal(this.thePrincipal);
        config.setPassword(this.thePassword);
        //config.setUseMulticast(false);

        try {
            config.addRdvSeedingURI(new URI("http://rdv.jxtahosts.net/cgi-bin/rendezvous.cgi?2"));
            config.addRelaySeedingURI(new URI("http://rdv.jxtahosts.net/cgi-bin/relays.cgi?2"));
        }
        catch (java.net.URISyntaxException use) {
            use.printStackTrace();
        }

        try {
            config.save();
        }
        catch (IOException io) {
            io.printStackTrace();
        }
    }
    else { // There is a pre-existing configuration file
        try {
            File pc = new File(home, "PlatformConfig");
            config.load(pc.toURI());
            // make changes if so desired
            // .........
            // store the PlatformConfig under the default home
            config.save();
        }
        catch (IOException io) {
            io.printStackTrace();
        }
        catch (CertificateException ce) {
            // In case the root cert is invalid, this creates a new one
            try {
                config.setPrincipal(this.thePrincipal);
                config.setPassword(this.thePassword);
                config.save();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    } //else pre-existing configuration
} //configure_jxta_node()


private void initialize_services()
{
    try {
        theRMPService = new JxtaReliableMcastService(netPeerGroup); //needs approximately on Intel Centrino Duo 1.66 347ms
        theRMPService.start_service();
    }
    catch (RMSException rex) {
        rex.printStackTrace();
    }

    long t1 = System.currentTimeMillis();
System.err.println(t1);
    if (create_channel)
        create(); //needs approximately on Intel Centrino Duo 1.66 547ms
    else
        join(); //needs approximately on Intel Centrino Duo 1.66 RESP_TIMEOUT_NORMAL + 1500ms inside a LAN

    long t2 = System.currentTimeMillis();
    System.err.println(t2);
System.err.println("***************** " + (t2-t1) + " *****************");
}


private void create() {
    try {
        theChannel = theRMPService.createChannel(channelID);
        theChannel.addRMSEventListener(this);
        System.out.println("\nThis peer is not only a provider[local-root], it is THE root-sender.");
        System.out.println("Please wait for others to connect and then type the message you want to propagate reliably hitting enter at the end.");
        System.out.println("You have to wait for a message like the following to be printed:");
        System.out.println("Peer :\"XXXXX\" connected to us");
    }
    catch (RMSException ex1) {
        ex1.printStackTrace();
        if(ex1 instanceof RMSCreateException)
            //doesn't need to call theChannel.closeChannel() because theChannel is null and not created
            _STOP();
    }
}



private void join() {
    try {
        theChannel = theRMPService.joinChannel(channelID, JxtaReliableMcastService.RESP_TIMEOUT_NORMAL, JxtaReliableMcastService.JOIN_RETRIES_NONPERSISTENT, this, true);
    }
    catch (RMSException ex1) {
        ex1.printStackTrace();
        if(ex1 instanceof RMSJoinException)
            //doesn't need to call theChannel.closeChannel() because theChannel is null and not created
            _STOP();
    }
    catch (InterruptedException ex2) {
        ex2.printStackTrace();
    }
}

private void begin()
{
        String a = "";

        while (true) {

            a = read_user_input();

            if (a.toLowerCase().trim().equals("close") && create_channel) {
                try {
                    theChannel.closeChannel();
                }
                catch (RMSException ex1) {
                    ex1.printStackTrace();
                }
                _STOP();
            }
            else
            if(a.toLowerCase().trim().equals("leave") && !create_channel) {
                try {
                    theChannel.leaveChannel();
                }
                catch (RMSException ex1) {
                    ex1.printStackTrace();
                }
                _STOP();
            }
            else
                try {
                    theChannel.send(a);
                }
                catch (RMSException ex2) {
                    ex2.printStackTrace();
                }
        }
}

private void _STOP()
{
    try {
        theRMPService.stop_service();
    }
    catch (RMSException ex) { //thrown if the channel is already stopped
        ex.printStackTrace();
    }
    netPeerGroup.stopApp();
    netPeerGroup.unref();
    netPeerGroup = null;
    System.exit(1);
}



public void processRMSEvent(JxtaReliableEvent e) {
    if (e.MessageType.compareTo("Channel Closure Message") == 0)
    {
            System.err.println("CHANNEL CLOSE HAS OCCURED");
            _STOP();
    }
    else {
        System.out.println("Received data : " + e.Data);
    }
}



/* -------------  THE MAIN FUNCTION SECTION  -------------------*/


    public static void main(String[] args) {
        String userin = "";
        String channel_id = "";
        int choice = UNACCEPTABLE_CHOICE;
        boolean create_ch = false;

        while(choice == UNACCEPTABLE_CHOICE)
        {
            print_choice_prompt();
            userin = read_user_input();
            choice = process_input(userin);
        }

        switch(choice)
        {
            case 0:{create_ch = true; break;}
            case 1:{create_ch = false; break;}
            default: {System.exit(1);};
        }

        System.out.print("\nPlease give the ID of the channel :> ");
        channel_id = read_user_input();
        RMP_TesterApp rmp_testerapp = new RMP_TesterApp(create_ch , channel_id);
        rmp_testerapp.startJxta(); //needs approximately on Intel Centrino Duo 1.66 3550ms
        rmp_testerapp.initialize_services();
        rmp_testerapp.begin();
    }


     private static int process_input(String userin) {
        int val = -1;
        try {
            val = Integer.parseInt(userin);
        }
        catch (NumberFormatException ex) {
            System.err.println("Not a valid number: " + userin);
            val=UNACCEPTABLE_CHOICE;
        }
        if((val < 0) || (val > 2))
        {
            val = UNACCEPTABLE_CHOICE;
            System.err.println("Not a valid number: " + userin);
        }
        return (val);
    }


    private static String read_user_input() {
        String user_str = null;
        try {
            BufferedReader is = new BufferedReader(new InputStreamReader(System.in));
            user_str = is.readLine();
        }
        catch (IOException e) {
            System.err.println("Unexpected IO ERROR: " + e);
            System.exit(1);
        }
        return (user_str.trim());
    }


    private static void print_choice_prompt() {
        System.out.println("\n\nRMP_TesterApp ....");
        System.out.println("Please make your choice");
        System.out.println("\tPress \"0\" to create a channel");
        System.out.println("\tPress \"1\" to join a channel");
        System.out.println("\tPress \"2\" to exit the program");
        System.out.print("Your choice :> ");
    }

}
