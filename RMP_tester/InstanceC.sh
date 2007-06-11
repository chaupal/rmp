#!/bin/sh
java -classpath "./lib/rmp.jar;/home/username/JXTA/lib/jxta.jar;/home/username/JXTA/lib/bcprov-jdk14.jar;/home/username/JXTA/lib/log4j.jar;." -DJXTA_HOME=jxta_peer_settings -DNODE_NAME=nodeC RMP_TesterApp
pause