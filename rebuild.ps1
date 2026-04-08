# Rebuild shortcut — run from D:\DBMS_project
$MVN = "D:\DBMS_project\maven-dist\apache-maven-3.9.6\bin\mvn.cmd"
& $MVN package -s "D:\DBMS_project\maven-settings.xml" -DskipTests
Write-Host "`n✔ Build done. Run with: java -jar target\ATM-Simulation-1.0-SNAPSHOT-jar-with-dependencies.jar"
