java -cp "lib/*" ^
--add-opens=java.base/java.io=ALL-UNNAMED ^
-Dserver.port=8280 ^
-Dspring.config.location=application.properties ^
-Dlogging.config=logback-spring.xml ^
-Dfeign.atp.executor.url=http://localhost:8180 ^
-Dmessage-broker.url=tcp://localhost:61617?wireFormat.maxInactivityDuration=0 ^
-Dhostname=%COMPUTERNAME% ^
org.qubership.automation.itf.Main
