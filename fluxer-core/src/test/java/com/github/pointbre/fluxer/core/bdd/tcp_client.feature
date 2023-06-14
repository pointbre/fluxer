Feature: TCP client

    Scenario: TCP client fails to start up if host is an empty string
        Given a free port 1 is found
        And TCP client 1 is created on an empty string at the found free port 1
        Then TCP client 1 cannot start

    Scenario: TCP client fails to start up if the server is not listening at the port
        Given a free port 1 is found
        And TCP client 1 is created at the found free port 1
        Then TCP client 1 cannot start

    Scenario: TCP client fails to start up if the port < 1024
        Given TCP client 1 is created at 1023
        Then TCP client 1 cannot start

    Scenario: TCP client fails to start up if the port > 65535
        Given TCP client 1 is created at 65536
        Then TCP client 1 cannot start
        
    #Scenario: TCP client starts up and then interact with TCP server
        #Given a free port 1 is found
        #And TCP server 1 is created at the found free port 1
        #And TCP client 1 is created at the found free port 1
        #When TCP server 1 starts successfully
        #And TCP client 1 starts successfully
        #And TCP client 1 writes a binary message "313233" to the TCP server 1
        #And TCP client 1 writes a binary message "343536" to the TCP server 1
        #And TCP client 1 stops successfully
        #And TCP server 1 stops successfully
        #Then TCP server 1 publishes its status changes: stopped -> starting -> started -> stopping -> stopped
        #And TCP server 1 publishes its link changes: connected -> disconnected
        #And TCP server 1 publishes its read changes: 2 binary messages "313233" and "343536"

