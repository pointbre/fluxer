Feature: TCP Server

    Scenario: Status flux publishes the current status of the TCP server
        Given a free port is found
        And TCP server is created on "localhost"
        When TCP server starts
        And TCP server stops
        Then status changes: stopped -> starting -> started -> stopping -> stopped
