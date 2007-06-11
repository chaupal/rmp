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
 *  $Id: ReconnectThread.java,v 1.1 2006/09/07 22:12:25 dimosp Exp $
 */



package rmp;

class ReconnectThread implements Runnable {
    private final static int RECONN_TIMEOUT_SHORT  = JxtaReliableMcastService.RESP_TIMEOUT_LONG + 4000;
    private final static int MAX_RECONN_LOOPS = JxtaReliableMcastService.JOIN_RETRIES_PERSISTENT;

    JxtaReliableMcastChannel theChannel;
    JxtaReliableMcastService theRMS;

    Object waitForNewProvidersLock = null;

    public ReconnectThread(JxtaReliableMcastChannel ch) {
        theChannel = ch;
        theRMS = ch.getRMS();
        waitForNewProvidersLock = new Object();
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used to
     * create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     *
     * @todo Implement this java.lang.Runnable method
     */
    public void run() {
        int loops = 0;

        if(!restore_Parent_bidi())
        {
            //In this point of the control, all the join efforts using the existing cached providers ()have failed.
           System.err.println("---> There are no more cached providers for trying to connec.");
           System.err.println("---> Resend joing requests via the multicast socket of the service");
           //Resend a join request message to the multicast socket of the JxtaReliableServices of the group to get new responses and add new providers to our cache
           theRMS.start_periodical_requests();
        }

        while(!restore_Parent_bidi() && (loops <= MAX_RECONN_LOOPS))
        {
            synchronized (waitForNewProvidersLock) {
                try {
                    waitForNewProvidersLock.wait(RECONN_TIMEOUT_SHORT);
                }
                catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
            loops++;
        }

        if((loops > MAX_RECONN_LOOPS))
        {
            theRMS.stop_periodical_requests();
            try {
                theChannel.terminate_channel(null, true);
            }
            catch (RMSException ex1) {
                ex1.printStackTrace();
            }
        }
    }

    private boolean restore_Parent_bidi()
    {
        boolean succ_reconnect = false;

        while( (!succ_reconnect) && (theChannel.getNumberOfProviders() > 0) )
        {
            succ_reconnect = theChannel.init_parental_pipe();
        }

        return succ_reconnect;
    }
}
