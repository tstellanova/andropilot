JAVABIN=/usr/lib/jvm/java-7-openjdk-amd64/jre/bin/java
SMCJAR=~/packages/state-machine-compiler/bin/Smc.jar

$JAVABIN -jar $SMCJAR -scala -d src/main/scala/com/geeksville/flight VehicleFSM.sm
$JAVABIN -jar $SMCJAR -graph -glevel 0 VehicleFSM.sm

dot -Tsvg VehicleFSM.dot >VehicleFSM.svg
dot -Tpng VehicleFSM.dot >VehicleFSM.png
rm VehicleFSM.dot

gnome-open VehicleFSM.svg
