#include "main.h"
#if defined(__32MX795F512L__)
#define SIZE_OF_PACKET_BUFFER 400
#elif defined(__32MX440F128H__)
#define SIZE_OF_PACKET_BUFFER 10
#endif

void updateCurrentPositions();
float getLinkScale(int index);


  int homingAllLinks = false;

  PACKET_FIFO_STORAGE packetFifo;
 BowlerPacket buffer[SIZE_OF_PACKET_BUFFER];
//BowlerPacket linTmpPack;
  BowlerPacket packetTemp;
  PD_VEL velCartesian[3];
  AbsPID taskPID[3];

//float scale = -1.0*mmPerTick;
//  float extrusionScale = 1/(((float)ticksPerRev)/100.00);

  float current[3];
  boolean full = false;
  boolean configured = false;


  int lastPushedBufferSize = 0;
  float lastXYZE[6];

  RunEveryData pid = {0, 100};

//  boolean keepCartesianPosition = false;
  int interpolationCounter = 0;
  boolean runKinematics = false;
  boolean pausePrint = false;

boolean onRunKinematicsSet(BowlerPacket *Packet) {
    runKinematics = Packet->use.data[0]; // Boolean to run the kinematics or not
    return true;
}

boolean setDesiredTaskSpaceTransform(BowlerPacket *Packet) {
//    println_E("setDesiredTaskSpaceTransform");
    float x = get32bit(Packet, 0) / 1000;
    float y = get32bit(Packet, 4) / 1000;
    float z = get32bit(Packet, 8) / 1000;
    //    float rx = get32bit(Packet,12)/1000;
    //    float ry = get32bit(Packet,16)/1000;
    //    float rz = get32bit(Packet,20)/1000;
    //    float w = get32bit(Packet,24)/1000;
    float ms = get32bit(Packet, 28);

    Packet->use.data[0] = 5;
    float t0 = 0, t1 = 0, t2 = 0;
    if (inverseKinematicsLocal(x, y, z, &t0, &t1, &t2) == 0) {
        setInterpolateXYZ(x, y, z, ms,true);
        set32bit(Packet, t0 * 1000, 1);
        set32bit(Packet, t1 * 1000, 5);
        set32bit(Packet, t2 * 1000, 9);

        set32bit(Packet, getLinkAngle(3)*1000, 13);
        set32bit(Packet, getLinkAngle(4)*1000, 17);
    } else {
//        println_E("Ignoring: Can't reach: x=");
//        p_fl_E(x);
//        print_E(" y=");
//        p_fl_E(y);
//        print_E(" z=");
//        p_fl_E(z);
    }
    return true;
}

boolean getCurrentTaskSpaceTransform(BowlerPacket *Packet) {
    //println_E("getCurrentTaskSpaceTransform");

    float x = 0, y = 0, z = 0;
    if (forwardKinematicsLocal(getLinkAngle(0), getLinkAngle(1), getLinkAngle(2), &x, &y, &z) == 0) {

        set32bit(Packet, x * 1000, 0);
        set32bit(Packet, y * 1000, 4);
        set32bit(Packet, z * 1000, 8);
        //rotation 0
        set32bit(Packet, 0 * 1000, 12);
        set32bit(Packet, 0 * 1000, 16);
        set32bit(Packet, 0 * 1000, 20);
        set32bit(Packet, 1 * 1000, 24);
    } else {
//        println_E("Ignoring: Can't reach: x=");
//        p_fl_E(x);
//        print_E(" y=");
//        p_fl_E(y);
//        print_E(" z=");
//        p_fl_E(z);
    }
    return true;
}

boolean setDesiredJointSpaceVector(BowlerPacket *Packet) {

    int numJoints = Packet->use.data[0];
    float j0 = get32bit(Packet, 1) / 1000;
    float j1 = get32bit(Packet, 5) / 1000;
    float j2 = get32bit(Packet, 9) / 1000;

    float extrusion = get32bit(Packet, 13) / 1000;
    float tempreture = get32bit(Packet, 17) / 1000;

    float ms = get32bit(Packet, 1 + numJoints * 4);
//    println_E("setDesiredJointSpaceVector ");
//    p_int_E(numJoints);
//    printPacket(Packet, ERROR_PRINT);

    float x = 0, y = 0, z = 0;
    if (forwardKinematicsLocal(j0, j1, j2, &x, &y, &z) == 0) {
        if (inverseKinematicsLocal(x, y, z, &j0, &j1, &j2) == 0) {
            setInterpolateXYZ(x, y, z, ms,true);
            setLinkAngle(3, extrusion, ms);
            setLinkAngle(4, tempreture, ms);
            // load data into packet
            set32bit(Packet, x * 1000, 0);
            set32bit(Packet, y * 1000, 4);
            set32bit(Packet, z * 1000, 8);
            //rotation 0
            set32bit(Packet, 0 * 1000, 12);
            set32bit(Packet, 0 * 1000, 16);
            set32bit(Packet, 0 * 1000, 20);
            set32bit(Packet, 1 * 1000, 24);
        }
    } else {
//        println_E("Ignoring: Can't reach x=");
//        p_fl_E(x);
//        print_E(" y=");
//        p_fl_E(y);
//        print_E(" z=");
//        p_fl_E(z);
    }
    return true;
}

boolean setDesiredJointAxisValue(BowlerPacket *Packet) {
//    println_E("setDesiredJointAxisValue");

    setLinkAngle(Packet->use.data[0], get32bit(Packet, 1) / 1000, get32bit(Packet, 5));

    return true;
}

boolean isCartesianInterpolationDone() {
    updateCurrentPositions();

    int setpointBound = 200;

    int i;
    for (i = 0; i < 4; i++) {
        if (i < 3) {

            if (getMs()>(taskPID[i].interpolate.startTime + taskPID[i].interpolate.setTime)) {
                // Interpolation has timed out, check for hard positioning
                if(useHardPositionSetteling() == false)
                    return true;
            }
            //check to see if it has arrived
            if (!bound(taskPID[i].interpolate.set,
                    current[i],
                    getmmPositionResolution(),
                    getmmPositionResolution())
                    ) {
                
                    // Not in position and not timed out
                    return false;
            }

        }
        if ((isPIDArrivedAtSetpoint(linkToHWIndex(i), setpointBound) == false) && (i == 3)) {
            //            println_W("LINK not done moving index = ");
            //            p_int_W(linkToHWIndex(i));
            //            print_W(" currently is = ");
            //            p_fl_W(getPidGroupDataTable(linkToHWIndex(i))->CurrentState);
            //            print_W(" heading towards = ");
            //            p_fl_W(getPidGroupDataTable(linkToHWIndex(i))->SetPoint);
            //            return false;
        }

    }
    return true;
}

void initializeCartesianController() {
    InitPacketFifo(&packetFifo, buffer, SIZE_OF_PACKET_BUFFER);
    int i = 0;
    for (i = 0; i < 3; i++) {
        taskPID[i].config.Enabled = true;
        taskPID[i].config.Polarity = true;
        InitAbsPIDWithPosition(&taskPID[i], getKP(), getKI(), getKD(), getMs(), 0);

    }
    pidReset(linkToHWIndex(3), 0);
}

void pushBufferEmpty() {
    LoadCorePacket(& packetTemp);
    packetTemp.use.head.Method = BOWLER_ASYN;
    packetTemp.use.head.MessageID = 1;
    packetTemp.use.head.RPC = GetRPCValue("_sli");
    INT32_UNION tmp;
    tmp.Val = lastPushedBufferSize;
    packetTemp.use.data[0] = tmp.byte.FB;
    packetTemp.use.data[1] = tmp.byte.TB;
    packetTemp.use.data[2] = tmp.byte.SB;
    packetTemp.use.data[3] = tmp.byte.LB;
    packetTemp.use.head.DataLegnth = 4 + 4;
    asyncCallback(&packetTemp);
}

void loadCurrentPosition(BowlerPacket * Packet) {
    LoadCorePacket(Packet);
    Packet->use.head.Method = BOWLER_STATUS;
    Packet->use.head.MessageID = 1;
    Packet->use.head.RPC = GetRPCValue("ctps");
    INT32_UNION tmp;
    tmp.Val = lastPushedBufferSize;
    Packet->use.data[0] = tmp.byte.FB;
    Packet->use.data[1] = tmp.byte.TB;
    Packet->use.data[2] = tmp.byte.SB;
    Packet->use.data[3] = tmp.byte.LB;

    tmp.Val = SIZE_OF_PACKET_BUFFER;
    Packet->use.data[4] = tmp.byte.FB;
    Packet->use.data[5] = tmp.byte.TB;
    Packet->use.data[6] = tmp.byte.SB;
    Packet->use.data[7] = tmp.byte.LB;

    Packet->use.head.DataLegnth = 4 + 4 + 4;
}

void updateCurrentPositions() {
    if (forwardKinematicsLocal(getLinkAngle(0),
            getLinkAngle(1),
            getLinkAngle(2),
            &current[0],
            &current[1],
            &current[2]) != 0) {
        //println_E("Forward Kinematics Failed!!");
        return;
    }
}

void checkPositionChange() {
    int i;
    float tmp[5];
    updateCurrentPositions();

    tmp[0] = current[0];
    tmp[1] = current[1];
    tmp[2] = current[2];
    tmp[3] = getLinkAngle(3);
    tmp[4] = getLinkAngle(4);
    tmp[5] = FifoGetPacketSpaceAvailible(&packetFifo);
    if (tmp[0] != lastXYZE[0] ||
            tmp[1] != lastXYZE[1] ||
            tmp[2] != lastXYZE[2] ||
            tmp[3] != lastXYZE[3] ||
            tmp[4] != lastXYZE[4]) {
        for (i = 0; i < 6; i++) {
            lastXYZE[i] = tmp[i];
        }

        //        println_I("Current Voltage of sensor");p_fl_E(getAdcVoltage(mapHeaterIndex(HEATER0_INDEX),10));
        //        print_E(" Temp = ");p_fl_E(getHeaterTempreture(HEATER0_INDEX));
        //        print_E(" Raw ADC = ");p_int_E(getAdcRaw(mapHeaterIndex(HEATER0_INDEX),10));

        //        println_I("Current  position X=");p_fl_E(lastXYZE[0]);
        //        print_E(" Y=");p_fl_E(lastXYZE[1]);
        //        print_E(" Z=");p_fl_E(lastXYZE[2]);
        //        print_E(" extr=");p_fl_E(lastXYZE[3]);
        INT32_UNION PID_Temp;
        LoadCorePacket(& packetTemp);
        packetTemp.use.head.DataLegnth = 4;
        packetTemp.use.head.RPC = GetRPCValue("cpos");
        int i;
        for (i = 0; i < 6; i++) {
            PID_Temp.Val = lastXYZE[i] *1000;
            packetTemp.use.data[0 + (i * 4)] = PID_Temp.byte.FB;
            packetTemp.use.data[1 + (i * 4)] = PID_Temp.byte.TB;
            packetTemp.use.data[2 + (i * 4)] = PID_Temp.byte.SB;
            packetTemp.use.data[3 + (i * 4)] = PID_Temp.byte.LB;
            packetTemp.use.head.DataLegnth += 4;
        }
        packetTemp.use.head.Method = BOWLER_ASYN;
        FixPacket(&packetTemp);
        asyncCallback(& packetTemp);
    }
}

void cartesianAsync() {
    if (RunEvery(&pid)) {
        int tmp = FifoGetPacketSpaceAvailible(&packetFifo);
        if (tmp != lastPushedBufferSize) {
            lastPushedBufferSize = tmp;
            pushBufferEmpty();
            full = false;
        }

        checkPositionChange();


    }
}

void processLinearInterpPacket(BowlerPacket * Packet) {
    if (Packet->use.head.RPC == _SLI) {
        //INT32_UNION tmp;
        float tmpData [5];
        int i;

        tmpData[0] = ((float) get32bit(Packet, 1));
        for (i = 1; i < 5; i++) {

            tmpData[i] = ((float) get32bit(Packet, (i * 4) + 1)) / 1000;
        }
        float t0, t1, t2;
        //set extruder
        setLinkAngle(3, tmpData[4], tmpData[0]);
        if (inverseKinematicsLocal(tmpData[1], tmpData[2], tmpData[3], &t0, &t1, &t2) == 0) {
            setInterpolateXYZ(tmpData[1], tmpData[2], tmpData[3], tmpData[0],Packet->use.data[0] );
        } else {
            ERR(Packet, 33, 34);
        }
    } else {
//        println_E("Wrong packet type!");
        printPacket(Packet, ERROR_PRINT);
    }
}

boolean onPausePrinter(BowlerPacket *Packet) {
    if (Packet->use.head.Method == BOWLER_POST) {
        pausePrint = Packet->use.data[0];
//        println_W("Pausing print ");
//        p_int_W(pausePrint);
        READY(Packet, 35, 35);
    } else {
        Packet->use.data[0] = pausePrint;
    }

    return true;
}

boolean onHomePrinter(BowlerPacket *Packet) {

    startHomingLinks();
    READY(Packet, 35, 36);
    return true;
}

boolean onClearPrinter(BowlerPacket *Packet) {
    Print_Level l = getPrintLevel();
    setPrintLevelInfoPrint();
    cancelPrint();
    READY(Packet, 35, 37);
    setPrintLevel(l);
    return true;
}

boolean onCartesianPost(BowlerPacket *Packet) {

    if (FifoGetPacketSpaceAvailible(&packetFifo) > 0) {
        if (Packet->use.data[0] == 1) {
            processLinearInterpPacket(Packet);
        } else {
//            println_I("Cached linear Packet ");
//            p_int_I(FifoGetPacketSpaceAvailible(&packetFifo));
            FifoAddPacket(&packetFifo, Packet);
        }
        Packet->use.head.Method = BOWLER_STATUS;
        INT32_UNION tmp;
        tmp.Val = FifoGetPacketSpaceAvailible(&packetFifo);
        Packet->use.data[0] = tmp.byte.FB;
        Packet->use.data[1] = tmp.byte.TB;
        Packet->use.data[2] = tmp.byte.SB;
        Packet->use.data[3] = tmp.byte.LB;

        tmp.Val = SIZE_OF_PACKET_BUFFER;
        Packet->use.data[4] = tmp.byte.FB;
        Packet->use.data[5] = tmp.byte.TB;
        Packet->use.data[6] = tmp.byte.SB;
        Packet->use.data[7] = tmp.byte.LB;

        Packet->use.head.DataLegnth = 4 + 4 + 4;
        if (tmp.Val == 0) {
            full = true;
        }

    } else {
//        println_I("###ERROR BUFFER FULL!!");
//        p_int_I(FifoGetPacketSpaceAvailible(&packetFifo));

        ERR(Packet, 33, 33);
    }
    return true;
}

void cancelPrint() {
    Print_Level l = getPrintLevel();

//    println_W("Cancel Print");
    setPrintLevel(l);
    InitPacketFifo(&packetFifo, buffer, SIZE_OF_PACKET_BUFFER);

    //ZeroPID(getHardwareMap()->Extruder0.index);
    //SetPIDTimed(getHardwareMap()->Heater0.index,0,0);
}

void runInterpolatedPositions() {
    float x = 0, y = 0, z = 0;
    float ms = getMs();

    x = interpolate(& taskPID[0].interpolate, ms);
    y = interpolate(& taskPID[1].interpolate, ms);
    z = interpolate(& taskPID[2].interpolate, ms);
    if (isCartesianInterpolationDone() == false) {
        setXYZ(x, y, z, 0);
    }
}

void setVelocity(int index, float jointSpace) {
    float value = (jointSpace /
            getLinkScale(index));
//    println_I("V ");
//    p_int_I(index);
//    print_I(" = ");
//    p_fl_I(jointSpace);
//    print_I(" : ");
//    p_fl_I(value);
    setOutput(linkToHWIndex(index),
            value);
}

float calculateTaskSpaceVelocityValue(int xyz) {
    //println_W(" ");
    //printXYZ(xyz);
    //print_W(" Data: ");
    float ms = getMs();
    taskPID[xyz].CurrentState = current[xyz];
    taskPID[xyz].SetPoint = interpolate(&taskPID[xyz].interpolate, getMs());
    RunAbstractPIDCalc(&taskPID[xyz], ms);

    float currentTarget = taskPID[xyz].SetPoint;
    float currentError = currentTarget - current[xyz];

    //runPdVelocityFromPointer(&velCartesian[xyz], current[xyz], getVKP(), getVKD());


    if (currentError > getmmPositionResolution() || currentError < -getmmPositionResolution()) {
        // println_E("\terror=   ");
        // p_fl_E(currentError);
        if (getMs()>(taskPID[xyz].interpolate.setTime + taskPID[xyz].interpolate.startTime)) {
            return taskPID[xyz].Output;
        } else {
            //print_W(" TIMED ");
            return taskPID[xyz].Output;
        }
    } else {
        //println_W("\terror=   ");
        //p_fl_W(currentError);
        return 0;
    }


}
RunEveryData velPrinter = {0, 500};

void runStateBasedController() {
    float Xd, Yd, Zd;
    float Ad, Bd, Cd;

    if (RunEvery(&velPrinter)) {
        //        setPrintLevelInfoPrint();
        //        clearPrint();
    }
    Xd = calculateTaskSpaceVelocityValue(0);
    Yd = calculateTaskSpaceVelocityValue(1);
    Zd = calculateTaskSpaceVelocityValue(2);

    if (velInverseLocal(current[0], current[1], current[2], Xd, Yd, Zd,
            & Ad, & Bd, & Cd) == 0) {
        int i = 0;
        for (i = 0; i < 3; i++) {
            SetPIDEnabled(linkToHWIndex(i), false);
        }
        setVelocity(0, Ad * getVKP());
        setVelocity(1, Bd * getVKP());
        setVelocity(2, Cd * getVKP());

    } else {
//        println_E("Inverse velocity kinematics failed");
    }
    //    setPrintLevelNoPrint();


}

void interpolateZXY() {
    updateCurrentPositions();
    interpolationCounter = 0;
    if (homingAllLinks == true || configured == false) {
        HomeLinks();
        return;
    }
    if (!runKinematics) {
        return;
    }

    if (kinematicsUseStateBasedVelocity() == true) {
        runStateBasedController();
    } else {
        runInterpolatedPositions();
    }
    if (isCartesianInterpolationDone() == true) {
        if (FifoGetPacketCount(&packetFifo) > 0) {
            if (pausePrint == false) {
//                println_W("Loading new packet ");
                if (FifoGetPacket(&packetFifo, &packetTemp)) {
                    processLinearInterpPacket(&packetTemp);
                }
            }
        }
    }

}

uint8_t setInterpolateXYZ(float x, float y, float z, float ms, boolean force) {
    int i = 0;
    float start = getMs();
    float valocity_calculated;
    updateCurrentPositions();
    float tmp[3] = {x, y, z};
    float mmToGo = 0;
//    println_W("Setting new position x=");
//    p_fl_W(x);
//    print_W(" y=");
//    p_fl_W(y);
//    print_W(" z=");
//    p_fl_W(z);
//    print_W(" Time MS=");
//    p_fl_W(ms);
//    println_W("Current  position cx=");
//    p_fl_W(current[0]);
//    print_W(" cy=");
//    p_fl_W(current[1]);
//    print_W(" cz=");
//    p_fl_W(current[2]);
    if(force == false){
        for (i = 0; i < 3; i++) {
            mmToGo = (tmp[i] - current[i]);
            if (mmToGo < 0)
                mmToGo *= -1;
            if (ms <= 0.0) {
                ms = (mmToGo / getmmaximumMMperSec())*1000.0;
//                println_W("Degenerate Capped to ");
//                p_fl_W(ms);
//                print_W(" mm=");
//                p_fl_W(mmToGo);
//                print_W(" max=");
//                p_fl_W(getmmaximumMMperSec());
            } else {
                valocity_calculated = (mmToGo /
                        (ms / 1000.0));
//                println_W("Setting new position FEED RATE =");
//                p_fl_W(valocity_calculated);
//                print_W(" mm=");
//                p_fl_W(mmToGo);
                if (valocity_calculated > getmmaximumMMperSec()) {

                    ms = (mmToGo / getmmaximumMMperSec()) * 1000.0;
//                    print_W(" Capped to=");
//                    p_fl_W(ms);
//                    print_W(" max=");
//                    p_fl_W(getmmaximumMMperSec());

                }
            }
        }
    }
    for (i = 0; i < 3; i++) {

        SetPIDTimedPointer(&taskPID[i], tmp[i], current[i], ms);
        printPIDvalsPointer(&taskPID[i]);

        velCartesian[i].unitsPerSeCond = valocity_calculated;
        velCartesian[i].currentOutputVel = valocity_calculated;
        velCartesian[i].lastTime = getMs();
        velCartesian[i].lastVelocity = 0;
        velCartesian[i].lastPosition = current[i];

    }

    runKinematics = true;

    if (ms == 0) {
//        println_I("Setting values directly");
        setXYZ(x, y, z, 0);
    } else {
        if (kinematicsUseStateBasedVelocity() == false) {
//            println_I("Setting values with linear interpolation");
            start = getMs();
            x = interpolate(& taskPID[0].interpolate, start);
            y = interpolate(& taskPID[1].interpolate, start);
            z = interpolate(& taskPID[2].interpolate, start);
            setXYZ(x, y, z, 0);
        } else {
//            println_I("Using the State-based velocity controller");

        }
    }

    printCartesianData();


    //setPrintLevelNoPrint();
    return 0;
}

uint8_t setXYZ(float x, float y, float z, float ms) {
    updateCurrentPositions();
    float t0 = 0, t1 = 0, t2 = 0;
    if (inverseKinematicsLocal(x, y, z, &t0, &t1, &t2) == 0) {
//        println_I("New target angles t1=");
//        p_fl_I(t0);
//        print_I(" t2=");
//        p_fl_I(t1);
//        print_I(" t3=");
//        p_fl_I(t2);
        setLinkAngle(0, t0, ms);
        setLinkAngle(1, t1, ms);
        setLinkAngle(2, t2, ms);
    } else {
//        println_E("Interpolate failed, can't reach: x=");
//        p_fl_E(x);
//        print_E(" y=");
//        p_fl_E(y);
//        print_E(" z=");
//        p_fl_E(z);
    }
    return 0;
}

float getLinkAngleNoScale(int index) {
    int localIndex = linkToHWIndex(index);
    return GetPIDPosition(localIndex);
}

float getLinkAngle(int index) {

    return getLinkAngleNoScale(index) * getLinkScale(index);
}

float setLinkAngle(int index, float value, float ms) {
    int localIndex = linkToHWIndex(index);
    float v = value / getLinkScale(index);

    SetPIDEnabled(localIndex, true);

    return SetPIDTimed(localIndex, v, ms);
}

void startHomingLinks() {
//    println_W("Homing links for kinematics");

    homingAllLinks = true;
    int i;
    float data[3];
    inverseKinematicsLocal(0, 0, getmaxZ(), &data[0], &data[1], &data[2]);
    for (i = 0; i < 3; i++) {
        startHomingLink(linkToHWIndex(i),
                CALIBRARTION_home_down,
                (data[i] + getRodLength() - 100) / getLinkScale(i));
    }
//    println_W("Started Homing...");
}

void HomeLinks() {
    int i;

    if (homingAllLinks) {

        if (GetPIDCalibrateionState(linkToHWIndex(0)) == CALIBRARTION_DONE &&
                GetPIDCalibrateionState(linkToHWIndex(1)) == CALIBRARTION_DONE &&
                GetPIDCalibrateionState(linkToHWIndex(2)) == CALIBRARTION_DONE
                ) {
            homingAllLinks = false;
            configured = true;
//            println_W("All linkes reported in");

            cancelPrint();
            setInterpolateXYZ(0, 0, getmaxZ(), 0,false);
        }
    }
}

void printXYZ(int xyz) {
//    switch (xyz) {
//        case 0:
//            print_W("X");
//            break;
//        case 1:
//            print_W("Y");
//            break;
//        case 2:
//            print_W("Z");
//            break;
//    }
}

void printCartesianData() {
    int i;
    updateCurrentPositions();

    float error = 0;
    for (i = 0; i < 3; i++) {
//        println_W(" ");
//        printXYZ(i);
//        print_W(" Data: ");
//        println_W("\tCurrent");
//        p_fl_W(current[i]);
//        println_W("\tTarget");
//        p_fl_W(taskPID[i].interpolate.set);
        error = taskPID[i].interpolate.set - current[i];
        if (bound(0, error, getmmPositionResolution(), getmmPositionResolution())) {
//            println_W("\tERROR=");
//            p_fl_W(error);
        } else {
//            println_E("\tERROR=");
//            p_fl_E(error);
        }
    }
}
