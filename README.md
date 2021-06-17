# AS_Proj3

<br>
<br>
<div style="text-align:center">
<img src="arquitetura.png" alt="Arquitectura" style="height: 400px; width:500px;"/>
</div>
<br>
<br>

## Messages

- Monitor and LB connect first
- Server starts and asks id to monitor.
- monitor tells LB about new server
- monitor sends heartbeats to each server
- client connects
- clients sends req to LB
- LB chooses server and sends req
- LB informes Monitor that req was send to server
- server sends response to LB
- LB informes Monitor about response
- LB sends response to client


Monitor -> LB           MONITOR|CONNECTION
Server -> Monitor       SERVER|ID_REQUEST
Monitor -> Server       MONITOR|serverId|serverPort
Monitor -> Server       MONITOR|HEARTBEAT
Server -> Server        SERVER|HEARTBEAT

client -> LB            CLIENT|clientId|requestId|serverId(00)|code(01)|nIter|avgConst(0)
LB -> Monitor           LOADBALANCER|SERVER_INFO
Monitor -> LB           MONITOR|{serverInfo}|...        {serverInfo} = serverId|serverPort|activeReqs
LB -> Server            LOADBALANCER|nIter
LB -> Monitor           LOADBALANCER|SENT_REQUEST|clientId|requestId|serverId|01|nIter|0
Server -> LB            SERVER|code|avgConst
LB -> Monitor           LOADBALANCER|RECEIVED_RESPONSE|clientId|requestId|serverId|code|nIter|avgConst
LB -> Client            LOADBALANCER|clientId|requestId|serverId|code|nIter|avgConst