Feature: TCP Server

    Scenario: TCP Server fails to start up if host is an empty string
        Given a free port 1 is found
        And TCP server 1 is created at 1023
        Then TCP server 1 can not start

    Scenario: TCP Server fails to start up if the port is already being used
        Given a free port 1 is found
        And TCP server 1 is created at the found free port 1
        And TCP server 2 is created at the found free port 1
        When TCP server 1 starts successfully
        Then TCP server 2 can not start

    Scenario: TCP Server fails to start up if the port < 1024
        Given TCP server 1 is created at 1023
        Then TCP server 1 can not start

    Scenario: TCP Server fails to start up if the port > 65535
        Given TCP server 1 is created at 65536
        Then TCP server 1 can not start

    Scenario: TCP Server starts up and publishes its status when started and stopped
        Given a free port 1 is found
        And TCP server 1 is created at the found free port 1
        When TCP server 1 starts successfully
        And TCP server 1 stops successfully
        Then TCP server 1 publishes its status changes: stopped -> starting -> started -> stopping -> stopped
        
    Scenario: TCP Server starts up and publishes its link when a client is connected and disconnected
        Given a free port 1 is found
        And TCP server 1 is created at the found free port 1
        And TCP client 1 is created at the found free port 1
        When TCP server 1 starts successfully
        And TCP client 1 starts successfully
        And TCP client 1 stops successfully
        And TCP server 1 stops successfully
        Then TCP server 1 publishes its link changes: connected -> disconnected
        
    Scenario: TCP Server starts up and publishes its incoming message when a connected client sends a text message
        Given a free port 1 is found
        And TCP server 1 is created at the found free port 1
        And TCP client 1 is created at the found free port 1
        When TCP server 1 starts successfully
        And TCP client 1 starts successfully
        And TCP client 1 writes a text message "123" to the TCP server 1
        And TCP client 1 stops successfully
        And TCP server 1 stops successfully
        Then TCP server 1 publishes its read changes: a text message "123"

    Scenario: TCP Server starts up and publishes its incoming message when a connected client sends a binary message
        Given a free port 1 is found
        And TCP server 1 is created at the found free port 1
        And TCP client 1 is created at the found free port 1
        When TCP server 1 starts successfully
        And TCP client 1 starts successfully
        And TCP client 1 writes a binary message "313233" to the TCP server 1
        And TCP client 1 stops successfully
        And TCP server 1 stops successfully
        Then TCP server 1 publishes its read changes: a binary message "313233"

    # status, link, read is alive while server is alive and not affected by client disconnection