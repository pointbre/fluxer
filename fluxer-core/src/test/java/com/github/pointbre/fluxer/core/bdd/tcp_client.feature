Feature: TCP client fluxer

    Scenario: TCP client fails to start up if host is an empty string
        When "TCP client 1" is created for an empty string host at the found free port number
        Then "TCP client 1" cannot start
        
    Scenario: TCP client fails to start up if host is null
        When "TCP client 1" is created for null host at the found free port number
        Then "TCP client 1" cannot start

    Scenario: TCP client fails to start up if the port <= 0
        When "TCP client 1" is created for "127.0.0.1" at the port number 0
        Then "TCP client 1" cannot start
        
    Scenario: TCP client fails to start up if the port >= 65536
        When "TCP client 1" is created for "127.0.0.1" at the port number 65536
        Then "TCP client 1" cannot start
        
    Scenario: TCP client fails to start up if TCP server is not listening
        When "TCP client 1" is created for "127.0.0.1" at the found free port number
        Then "TCP client 1" cannot start
      
		Scenario: TCP client publishes status changes
		    Given a free port number is found
    	  When "TCP server 1" is created for "127.0.0.1" at the given port number
        And "TCP server 1" starts successfully
        And a new subscriber registers to status of "TCP server 1"
        And "TCP client 1" is created for "127.0.0.1" at the given port number
        And "TCP client 1" starts successfully
        And a new subscriber registers to status of "TCP client 1"
        And "TCP client 1" stops successfully
        And "TCP server 1" stops successfully
        Then the subscriber of status of "TCP client 1" receives: STOPPED -> STARTING -> STARTED -> STOPPING -> STOPPED
        And the subscriber of status of "TCP server 1" receives: STOPPED -> STARTING -> STARTED -> STOPPING -> STOPPED
        
    #Scenario: 1
        #Given a free port 1 is found
        #And TCP server 1 is created at the found free port 1
        #And TCP client 1 is created at the found free port 1
        #When TCP server 1 starts successfully
        #And TCP client 1 starts successfully
        #And TCP client 1 writes a binary message "313233" to the TCP server 1
        #And TCP server 1 writes a binary message "414243" to the TCP client 1
        #And TCP client 1 writes a binary message "343536" to the TCP server 1
        #And TCP client 1 stops successfully
        #And TCP server 1 stops successfully
        #Then TCP server 1 publishes its status changes: stopped -> starting -> started -> stopping -> stopped
#
    #Scenario: 2
        #Given a free port 1 is found
        #And TCP server 1 is created at the found free port 1
        #And TCP client 1 is created at the found free port 1
        #When TCP server 1 starts successfully
        #And TCP client 1 starts successfully
        #And TCP client 1 stops successfully
        #And TCP server 1 stops successfully
        #And TCP server 1 publishes its link changes: connected -> disconnected
#
    #Scenario: 3
        #Given a free port 1 is found
        #And TCP server 1 is created at the found free port 1
        #And TCP client 1 is created at the found free port 1
        #When TCP server 1 starts successfully
        #And TCP client 1 starts successfully
        #And TCP client 1 writes a binary message "313233" to the TCP server 1
        #And TCP client 1 stops successfully
        #And TCP server 1 stops successfully
        #Then TCP server 1 publishes its read changes: 1 binary message "313233"
        
