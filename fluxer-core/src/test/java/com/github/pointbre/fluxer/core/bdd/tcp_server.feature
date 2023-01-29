Feature: TCP Server

    Scenario: TCP Server fails to start up if host is ""
        Given a free port 1 is found
        And TCP server 1 is created on "" at 1023
        Then TCP server 1 can not start

    Scenario: TCP Server fails to start up if the port is already being used
        Given a free port 1 is found
        And TCP server 1 is created on "localhost" at the found free port 1
        And TCP server 2 is created on "localhost" at the found free port 1
        When TCP server 1 starts successfully
        Then TCP server 2 can not start

    Scenario: TCP Server fails to start up if the port < 1024
        Given TCP server 1 is created on "localhost" at 1023
        Then TCP server 1 can not start

    Scenario: TCP Server fails to start up if the port > 65535
        Given TCP server 1 is created on "localhost" at 65536
        Then TCP server 1 can not start

    Scenario: TCP Server starts up and publishes its status when started and stopped
        Given a free port 1 is found
        And TCP server 1 is created on "localhost" at the found free port 1
        When TCP server 1 starts successfully
        And TCP server 1 stops successfully
        Then TCP server 1's status changes: stopped -> starting -> started -> stopping -> stopped