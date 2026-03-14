API

This wiki page describes all available API functions.
Please contact Unwired Labs if you have any suggestions for improvement or bugs to report.

API key
Most of the API calls require an API key. This API key can be obtained by registering here.
All uploaded data will be stored along with this API key. This offers many functions, including the deletion of data from users that provide incorrect data.
It also allows the identification of users that are violating the community server usage policy.

Some smartphone apps also require an API key, for example the Tower Collector.

In the examples below "apiKey" is used as a place holder for this parameter.

WebServiceURL
The base URL (<WebServiceURL>) for the OpenCellID API service. This should be either http://opencellid.org or https://opencellid.org.
Note: HTTPS is recommended for secure communication.

"changeable" attribute
The meaning of the "changeable" value returned by some methods is the following:

some cell positions in the database are precise; for example, the GSM network provider O2 in Germany provides GPS information along with the Cell ID information and software is able to extract this information for OpenCellID.
in this case, new measurements for the cell are stored and shown on the map; however, their GPS positions are not used for calculating the GPS position of the cell tower.
changeable=1:
the GPS position of the cell tower has been calculated from all available measurements

changeable=0:
the GPS position of the cell tower is precise; no measurements have been used to calculate it.

Filtering of data
The following filters are currently applied to all incoming data and decide if a measurement is suspicious or valid.
Suspect data is inserted into a database table with suspect data which can be re-examined at a later stage if required.

Parameter	Rules
act	must be one of supported type:
1xRTT, CDMA, eHRPD, IS95A, IS95B, EVDO_0, EVDO_A, EVDO_B, UMTS, HSPA+, HSDPA, HSUPA, HSPA, LTE, LTECATM, NR, NBIOT, EDGE, GPRS, GSM
MCC	must be known in the database
MCC	must be in the range of 100 to 999
MNC/SID	must be known in the database
MNC	must be in the range of 0 to 999 for GSM, UMTS, LTE, NR and NBIOT
MNC/SID	must be in the range of 0 to 32767 for CDMA
LAC/TAC/NID	must be in the range of 1 to 65535 for all radios except NR, 0 to 16777215 for NR.
CID	must be in the range of 1 to 65535 for GSM
CID	must be in the range of 1 to 268435455 for UMTS and LTE, 1 to 68719476735 for NR
CID/BID	must be in the range 1 to 65535 for CDMA
longitude	must be float (not integer)
longitude	must be in the range: (-180,0) U (0,180)
longitude	must be != 0
latitude	must be float (not integer)
latitude	must be in the range: (-90,0) U (0,90)
latitude	must be != 0
Parameters	Rules
MCC,MNC,LAC,CID,longitude,latitude	must have unique coordinates for the cell
MCC,longitude,latitude	must have coordinates in the home country
MCC,MNC,LAC,CID,longitude,latitude	must be closer than 150 km away from the cell (only in case of precise cells)
The following filters are currently applied to optional fields in all incoming data.
If an optional field has invalid value, then only the value is rejected.

Parameter	Rules
measured_at	must be in one of supported date formats:
- timestamp (milliseconds since 1/1/1970 00:00:00 GMT),
- "yyyy-MM-dd HH:mm:ss",
- "yyyyMMddHHmmss",
- "yyyy-MM-dd HH:mm:ss.SSSZ",
- "yyyy-MM-dd"
signal	
Technology	Rules
GSM	RSSI in dBm in the range of -51 to -113 or ASU in the range of 0 to 31
UMTS	RSCP in dBm in the range of -25 to -121 or ASU in the range of -5 to 91
LTE	RSRP in dBm in the range of -45 to -137 or ASU in the range of 0 to 95
NR	RSRP in dBm in the range of -44 to -140 or ASU in the range of 0 to 97
CDMA	RSSI in dBm in the range of -75 to -100 or ASU in the range of 1 to 16
rating	must be in metres in the range of 0 to 35000
speed	must be in metres/second in the range of 0 to 300
direction	must be in the range of 0 to 360
ta	only for GSM and LTE; must be in the range of 0 to 63
psc	only for UMTS; must be in the range of 0 to 511
pci	only for LTE; must be in the range of 0 to 503
txp	must be in the range of -200 to 100
tsrf	must be in the range of -100 to 100
Payload legend
A typical payload for API requests and Measurement submissions may include some or all of the following parameters:

Parameter	Data type	Description	Optional
<apiKey>	string	API key assigned to the user submitting the measurement	no
<lat>	double	Latitude	no
<lon>	double	Longitude	no
<mcc>	integer	Mobile country code	no
(except CDMA)
<mnc>	integer	Mobile network code or system identifier	no
(except CDMA if sid is provided)
<lac>	integer	Local area code, tracking area code or network id	no
(except CDMA if nid is provided or LTE or NR if tac is provided)
<cellid>	integer	Cell tower id or base station id	no
(except CDMA if bid is provided)
<signal>	integer	Signal level: either in dBm or as defined in TS 27.007 8.5; both is accepted.	yes
<measured_at>	date	When the measurement was measured.
Supported date formats:
- timestamp (milliseconds since 1/1/1970 00:00:00 GMT)
- "yyyy-MM-dd HH:mm:ss"
- "yyyyMMddHHmmss"
- "yyyy-MM-dd HH:mm:ss.SSSZ"
- "yyyy-MM-dd"
Time zone: UTC	yes
<rating>	double	GPS quality/accuracy information (metres)	yes
<speed>	double	Speed when creating the measurement, in metres/second	yes
<direction>	double	Heading direction when creating the measurement
(0=north, 90=east)	yes
<act>	string	Network access type; currently supported:
1xRTT, CDMA, eHRPD, IS95A, IS95B, EVDO_0, EVDO_A, EVDO_B, UMTS, HSPA+, HSDPA, HSUPA, HSPA, TDSCDMA, LTE, LTECATM, NR, NBIOT, EDGE, GPRS, GSM
Technology	Type	Description
CDMA	1xRTT	single-carrier Radio Transmission Technology (2.5G)
CDMA	CDMA (includes IS95A, IS95B)	Code division multiple access
CDMA	eHRPD	Enhanced High Rate Packet Data
CDMA	EVDO_0	Evolution-Data Optimized Revision 0
CDMA	EVDO_A	Evolution-Data Optimized Revision A
CDMA	EVDO_B	Evolution-Data Optimized Revision B
UMTS	UMTS	UMTS access
UMTS	HSPA+	High speed Packet Access
UMTS	HSDPA	High Speed Downlink Packet Access
UMTS	HSUPA	High Speed Uplink Packet Access
UMTS	HSPA	= "HSDPA/HSUPA" = High Speed Packet Access (both downlink and uplink)
UMTS	TDSCDMA	= "TDSCDMA" = Time Division Synchronous Code Division Multiple Access (TD-SCDMA)
LTE	LTE	Long term evolution
LTE	LTECATM	Long term evolution CAT-M network
NR	NR	New Radio (5G)
NBIOT	NBIOT	Narrowband IoT
iDEN	iDEN	Integrated Digital Enhanced Network
GSM	EDGE	Enhanced Data Rates for GSM Evolution
GSM	GPRS	GPRS access
yes
<ta>	integer	Timing advance; only for GSM and LTE	yes
<psc>	integer	Primary scrambling code; only for UMTS and TDSCDMA	yes
<tac>	integer	Tracking area code; only for LTE or NR	yes
<pci>	integer	Physical cell Id; only for LTE or NR	yes
<sid>	integer	System identifier; only for CDMA	yes
<nid>	integer	Network id; only for CDMA	yes
<bid>	integer	Base station id; only for CDMA	yes
<devn>	string	Device name as concatenated strings with the manufacturer and the model name; max 50 characters	yes
<txp>	integer	TX power in dBm	yes
<tsrf>	integer	Temperature in the RF module; in degrees Celsius; only with <txp> parameter	yes


Error codes
The following error codes might be returned by OpenCellID services:

Error code	HTTP code	Description
1	200	Cell not found
2	401	Invalid API key
3	400	Invalid input data
4	403	Your API key must be white listed in order to run this operation. This service is free of charge as long as your application contributes to the OpenCellID project. Should your application meet this requirement, please contact markus@opencellid.org for the authorisation of your API key.

For further details please visit:
https://wiki.opencellid.org/wiki/Commercial_users
https://wiki.opencellid.org/wiki/Request_whitelisting

You can alternatively download the database here:
https://opencellid.org/downloads/
5	500	Internal server error
6	503	Too many requests. Try later again.
7	429	Daily limit 1000 requests exceeded for your API key.

For further details please visit:
https://wiki.opencellid.org/wiki/Commercial_users

You can alternatively download the database here:
https://opencellid.org/downloads/
Adding a single measurement
GET/POST request to

https://<WebServiceURL>/measure/add
Parameters
<WebServiceURL>: the URL to the web service

Payload
key=<apiKey>&lat=<lat>&lon=<lon>&mcc=<mcc>&mnc=<mnc>&lac=<lac>&cellid=<cellid>&signal=<signal>&measured_at=<measured_at>&rating=<rating>&speed=<speed>&direction=<direction>&act=<act>&ta=<ta>&psc=<psc>&tac=<tac>&pci=<pci>&sid=<sid>&nid=<nid>&bid=<bid>&devn=<devn>&txp=<txp>&tsrf=<tsrf>

Where



Response
Upon successful insert, HTTP 200 is returned with the string "Your measurement has been inserted."

Example
https://opencellid.org/measure/add?key=xxx&lat=10.11&lon=11.12&mcc=100&mnc=10&lac=11&cellid=12&rating=10&direction=90&act=GPRS
https://opencellid.org/measure/add?key=xxx&lat=10.11&lon=11.12&mcc=100&mnc=10&lac=11&cellid=12&rating=10.1&direction=90.3&speed=12.3&act=GPRS&ta=3
https://opencellid.org/measure/add?key=xxx&lat=10.11&lon=11.12&mcc=100&mnc=10&lac=11&cellid=12&rating=10.1&direction=90.3&speed=12.3&act=HSPA+&psc=3
https://opencellid.org/measure/add?key=xxx&lat=10.11&lon=11.12&mcc=100&mnc=10&lac=11&cellid=12&rating=10.1&direction=90.3&speed=12.3&act=LTE&pci=3
https://opencellid.org/measure/add?key=xxx&lat=10.11&lon=11.12&mcc=100&mnc=10&tac=11&cellid=12&rating=10.1&direction=90.3&speed=12.3&act=LTE&pci=3
https://opencellid.org/measure/add?key=xxx&lat=10.11&lon=11.12&mcc=100&mnc=10&lac=11&cellid=12&rating=10.1&direction=90.3&speed=12.3&act=1xRTT
https://opencellid.org/measure/add?key=xxx&lat=10.11&lon=11.12&mcc=100&sid=10&nid=11&bid=12&rating=10.1&direction=90.3&speed=12.3&act=1xRTT
https://opencellid.org/measure/add?key=xxx&lat=10.11&lon=11.12&sid=10&nid=11&bid=12&rating=10.1&direction=90.3&speed=12.3&act=eHRPD
https://opencellid.org/measure/add?key=xxx&lat=10.11&lon=11.12&mcc=100&mnc=10&lac=11&cellid=12&act=GSM&devn=Samsung%20GT-S5830L&txp=-45&tsrf=22

Time needed for processing newly uploaded measurements
After uploading new measurements to the OpenCellID community servers they are added to our waiting queues and processed in the background. We aim the real-time processing, but maximum time depends on traffic on OpenCellID servers. Current throughput of our servers is around 15000 measurements per second.

Measurements are processed in 3 steps:

parsing and filtering raw data
updating measurement statistics and storing measurements in the database
updating existing cell towers information or adding new cell towers to the database
Our waiting queues for cell towers and for measurements are independent, so you can see your measurements in the OpenCellID database but related cell towers might be not updated yet. This means that if you upload a measurement of a cell tower that was not in the database before and the waiting queue for cell towers has some data to process, you have to expect some times (usually few minutes) until the queue delivers the newly uploaded cell tower information.

Uploading measurements from a CSV file
POST request to

https://<WebServiceURL>/measure/uploadCsv
Parameters
<WebServiceURL>: the URL to the web service

Payload
CSV file of the following format:

mcc,mnc,lac,cellid,lon,lat,signal,measured_at,rating,speed,direction,act,ta,psc,tac,pci,sid,nid,bid
100,2,434,9200,9.436598,52.892139,4,1389472208000,5.4,0,0,GPRS,3,,,,,,
100,2,434,9200,9.441658,52.890351,4,1389472225000,20.1,10.4,90.1,HSPA+,,4,,,,,
100,2,434,9200,9.441658,52.890351,4,1389472225000,20.1,10.4,90.1,LTE,,,,4,,,
100,2,,9200,9.441658,52.890351,4,1389472225000,20.1,10.4,90.1,LTE,,,434,4,,,
100,2,432,9200,9.441658,52.890351,4,1389472225000,20.1,10.4,90.1,LTE,,,432,4,,,
100,1234,432,9201,9.441658,52.890351,4,1389472225000,20.1,10.4,90.1,eHRPD,,,,,,,
100,1234,432,9201,9.441658,52.890351,4,1389472225000,20.1,10.4,90.1,eHRPD,,,,,1234,432,9201
100,,,,9.441658,52.890351,4,1389472225000,20.1,10.4,90.1,1xRTT,,,,,1234,432,9201
,,,,9.441658,52.890351,4,1389472225000,20.1,10.4,90.1,eHRPD,,,,,1234,432,9201
...
Column names recognised:
mcc, mnc, lac, cellid, lat, lon, signal, measured_at, rating, speed, direction, act, ta, psc, tac, pci, sid, nid, bid

Maximum file size:
2 MB; upload multiple files if you want to upload more data.

Supplemented by the parameter key=<apiKey> as form POST field, which is the user's API key.



Response
Upon successful insert, HTTP 200 is returned with the string "0,OK".

Business logic
The request is handled by de.enaikoon.gpssuite.web.measure.NewMeasureController.

cURL command
curl -F "key=apiKey" -F "datafile=@/path/to/file/mydata.csv;type=text/plain" https://opencellid.org/measure/uploadCsv
Time needed for processing newly uploaded measurements
After uploading new measurements to the OpenCellID community servers they are added to our waiting queues and processed in the background. We aim the real-time processing, but maximum time depends on traffic on OpenCellID servers. Current throughput of our servers is around 15000 measurements per second.

Measurements are processed in 3 steps:

parsing and filtering raw data
updating measurement statistics and storing measurements in the database
updating existing cell towers information or adding new cell towers to the database
Our waiting queues for cell towers and for measurements are independent, so you can see your measurements in the OpenCellID database but related cell towers might be not updated yet. This means that if you upload a measurement of a cell tower that was not in the database before and the waiting queue for cell towers has some data to process, you have to expect some times (usually few minutes) until the queue delivers the newly uploaded cell tower information.

FAQ
What does "optional" mean?
"optional" means that this field is not required in the csv file.
Minimal csv file looks as follows:

mcc,mnc,lac,cellid,lon,lat
262,2,434,9200,9.436598,52.892139
What does "Supplemented by the parameter key=<apiKey> as form POST field, which is the user's API key" mean?
This means that the user must send his data (apiKey and csv file) in the same way as from the following form:

<form method="post" action="https://opencellid.org/measure/uploadCsv" enctype="multipart/form-data">
  apiKey: <input type="text" name="key" />
  CSV (max 2MB): <input type="file" name="datafile" />
  <input type="submit" name="upload" value="Upload"/>
</form>
What does the following error mean?

<?xml version="1.0" encoding="UTF-8"?>
<rsp stat="fail"><err info="cell not found" code="1"/></rsp>
This error message means that no cell was found for the given user criteria (mcc-mnc-lac-cid combination was not found in database).

Can I upload CDMA using mcc-mnc-lac-cellid parameters?
Yes, you can use mnc as sid, lac as nid and cellid as bid.

Uploading measurements from a JSON file
POST request to

https://<WebServiceURL>/measure/uploadJson
Parameters
<WebServiceURL>: the URL to the web service

Payload
JSON file (send in datafile parameter) of the following format:

{
    "measurements": [
        {
            "lon": 9.436598,
            "lat": 52.892139,
            "mcc": 100,
            "mnc": 2,
            "lac": 434,
            "cellid": 9200,
            "measured_at": 1389472224000,
            "signal": -80,
            "rating": 5.4,
            "speed": 2.32,
            "direction": 93.22,
            "act": "EDGE",
            "ta": 3
        },
        {
            "lon": 9.441658,
            "lat": 52.890351,
            "mcc": 100,
            "mnc": 2,
            "lac": 3,
            "cellid": 9200,
            "measured_at": 1389472225000,
            "signal": -80,
            "speed": 11.21,
            "act": "HSDPA",
            "psc": 2
        },
        {
            "lon": 9.441658,
            "lat": 52.890351,
            "mcc": 100,
            "mnc": 2,
            "lac": 321,
            "cellid": 9200,
            "measured_at": 1389472226000,
            "signal": -80,
            "rating": 11.21,
            "act": "LTE",
            "pci": 2
        },
        {
            "lon": 9.441658,
            "lat": 52.890351,
            "mcc": 100,
            "mnc": 2,
            "tac": 321,
            "cellid": 9200,
            "measured_at": 1389472227000,
            "signal": -80,
            "act": "LTE"
        },
        {
            "lon": 9.441658,
            "lat": 52.890351,
            "mcc": 100,
            "mnc": 12345,
            "lac": 1,
            "cellid": 2,
            "measured_at": 1389472228000,
            "signal": -80,
            "act": "eHRPD"
        },
        {
            "lon": 9.441658,
            "lat": 52.890351,
            "mcc": 100,
            "sid": 12345,
            "nid": 1,
            "bid": 2,
            "measured_at": 1389472229000,
            "signal": -80,
            "act": "EVDO_0"
        }
    ]
}
Field names recognised:
mcc, mnc, lac, cellid, lat, lon, signal, measured_at, rating, speed, direction, act, ta, psc, tac, pci, sid, nid, bid

Maximum file size:
2 MB; upload multiple files if you want to upload more data.

Supplemented by the parameter key=<apiKey> as form POST field, which is the user's API key.

Response
Upon successful insert, HTTP 200 is returned with application/json content type and following json document as a content: {"code":0,"status":"OK"}

Business logic
The request is handled by de.enaikoon.gpssuite.web.measure.NewMeasureController.

cURL command
curl -F "key=apiKey" -F "datafile=@/path/to/file/mydata.json;type=text/plain" https://opencellid.org/measure/uploadJson
Time needed for processing newly uploaded measurements
After uploading new measurements to the OpenCellID community servers they are added to our waiting queues and processed in the background. We aim the real-time processing, but maximum time depends on traffic on OpenCellID servers. Current throughput of our servers is around 15000 measurements per second.

Measurements are processed in 3 steps:

parsing and filtering raw data
updating measurement statistics and storing measurements in the database
updating existing cell towers information or adding new cell towers to the database
Our waiting queues for cell towers and for measurements are independent, so you can see your measurements in the OpenCellID database but related cell towers might be not updated yet. This means that if you upload a measurement of a cell tower that was not in the database before and the waiting queue for cell towers has some data to process, you have to expect some times (usually few minutes) until the queue delivers the newly uploaded cell tower information.

FAQ
What does "optional" mean?
"optional" means that this field is not required in the json file.
Minimal json file looks as follows:

{
    "measurements": [
        {
            "lon": 9.436598,
            "lat": 52.892139,
            "mcc": 262,
            "mnc": 2,
            "lac": 434,
            "cellid": 9200
        }
    ]
}
What does "Supplemented by the parameter key=<apiKey> as form POST field, which is the user's API key" mean?
This means that the user must send his data (apiKey and json file) in the same way as from the following form:

<form method="post" action="https://opencellid.org/measure/uploadJson" enctype="multipart/form-data">
  apiKey: <input type="text" name="key" />
  JSON (max 2MB): <input type="file" name="datafile" />
  <input type="submit" name="upload" value="Upload"/>
</form>
What does the following error mean?

<?xml version="1.0" encoding="UTF-8"?>
<rsp stat="fail"><err info="cell not found" code="1"/></rsp>
This error message means that no cell was found for the given user criteria (mcc-mnc-lac-cid combination was not found in database).

Can I upload CDMA using mcc-mnc-lac-cellid parameters?
Yes, you can use mnc as sid, lac as nid and cellid as bid.

Uploading measurements from CLF3 file
POST request to

https://<WebServiceURL>/measure/uploadClf
Parameters
<WebServiceURL>: The URL to the web service

Payload
CLF version 3.0 file (to be sent in datafile parameter) of the following format:

//mcc+mnc;cellid;lac;rnc;lat;lon;ratio;data;rfu
26202;07812;03101;0;45.894375;31.51312;0;City Square;0
Columns are semicolon separated and have the following strict order:
mcc+mnc, lac, cellid, rnc, lat, lon, ratio, data, rfu

There is no header in CLF3 files. Therefore the columns must be provided in the order given above and all values must be there.

You can add comments to CLF3 files.
Comments in CLF3 start with // and extend to the end of the physical line.

Maximum file size:
2 MB; upload multiple files if you want to upload more data.

Supplemented by the parameter key=<apiKey> as form POST field, which is the user's API key

Supported columns:
Column name	Data type	Description	Optional
<mcc+mnc>	string	Mobile country code and mobile network code	no
lac	integer	Local area code	no
cellid	integer	Cell tower id	no
rnc	integer	Radio network controller;
provide "0", (zero, without the quotation marks) or an empty string in case there is no rnc value.	no
lat	double	Latitude	no
lon	double	Latitude	no
ratio	integer	Accuracy of the cell coordinates. Use -1 for the exact position.	yes
data	string	Short cell description	yes
rfu	string	Not really used; use 0 if unknown	yes
Response
On successful insert return HTTP 200 with the string "0,OK".

Business logic
The request is handled by de.enaikoon.gpssuite.web.measure.NewMeasureController.

Time needed for processing newly uploaded measurements
After uploading new measurements to the OpenCellID community servers they are added to our waiting queues and processed in the background. We aim the real-time processing, but maximum time depends on traffic on OpenCellID servers. Current throughput of our servers is around 15000 measurements per second.

Measurements are processed in 3 steps:

parsing and filtering raw data
updating measurement statistics and storing measurements in the database
updating existing cell towers information or adding new cell towers to the database
Our waiting queues for cell towers and for measurements are independent, so you can see your measurements in the OpenCellID database but related cell towers might be not updated yet. This means that if you upload a measurement of a cell tower that was not in the database before and the waiting queue for cell towers has some data to process, you have to expect some times (usually few minutes) until the queue delivers the newly uploaded cell tower information.

Getting cell position
GET/POST request to

https://<WebServiceURL>/cell/get
Parameters
<WebServiceURL>: the URL to the web service

Headers
JSON:
Content-Type: application/json;charset=UTF-8

XML:
Content-Type: text/xml;charset=utf-8

AT:
Content-Type: text/plain;charset=utf-8

Payload
key=<apiKey>&mcc=<mcc>&mnc=<mnc>&lac=<lac>&cellid=<cellid>&radio=<radio>&format=<format>

Where

Parameter	Data type	Description	Optional
<apiKey>	string	API key assigned to the user	no
<mcc>	integer	Mobile country code	no
<mnc>	integer	Mobile network code or system identifier	no
<lac>	integer	Local area code, tracking area code or network id	no
<cellid>	integer	Cell id or base station id	no
<radio>	string	You can specifiy GSM, UMTS, LTE, NBIOT, NR or CDMA as the radio of returned cell. Otherwise first matched cell will be returned.	yes
<format>	string	You can specifiy xml, at or json as output. Default is xml.	yes
Response
XML
Upon successful request, HTTP 200 is returned with XML of the following format:

<?xml version="1.0" encoding="UTF-8"?>
<rsp stat="ok">
  <cell lat="52.891587" 
        lon="9.438145" 
        mcc="262" 
        mnc="2" 
        lac="434" 
        cellid="9200" 
        averageSignalStrength="-81" 
        range="123" 
        samples="13" 
        changeable="1" 
        radio="GSM"/>
</rsp>


<?xml version="1.0" encoding="UTF-8"?>
<rsp stat="ok">
  <cell lat="51.206" 
        lon="20.424" 
        mcc="260" 
        mnc="2" 
        lac="52712" 
        cellid="59350690" 
        averageSignalStrength="-96" 
        range="1234" 
        samples="5" 
        changeable="1" 
        radio="UMTS" 
        rnc="905" 
        cid="40610"/>
</rsp>


<?xml version="1.0" encoding="UTF-8"?>
<rsp stat="ok">
  <cell lat="51.90994" 
        lon="19.505" 
        mcc="260" 
        mnc="6" 
        lac="12" 
        cellid="864246" 
        averageSignalStrength="-100" 
        range="12345" 
        samples="4" 
        changeable="1" 
        radio="LTE" 
        tac="12"/>
</rsp>


<?xml version="1.0" encoding="UTF-8"?>
<rsp stat="ok">
  <cell lat="42.46" 
        lon="-73.245" 
        mcc="310" 
        mnc="119" 
        lac="48" 
        cellid="4127" 
        averageSignalStrength="-90" 
        range="123" 
        samples="5" 
        changeable="1" 
        radio="CDMA" 
        sid="119" 
        nid="48" 
        bid="4127"/>
</rsp>
JSON
{
  "lon": -73.245,
  "lat": 42.46,
  "mcc": 310,
  "mnc": 119,
  "lac": 48,
  "cellid": 4127,
  "averageSignalStrength": -90,
  "range": 123,
  "samples": 5,
  "changeable": true,
  "radio": "CDMA",
  "sid": 119,
  "nid": 48,
  "bid": 4127
}
AT
+Location:42.46,-73.245,1483228800
Business logic
The request is handled by de.enaikoon.gpssuite.web.cell.CellController.
It uses de.enaikoon.m2m.gsmcellmanagment.dao.mongodb.GsmCellDao to calculate the average cell measurement, and builds an XML string to return to the user.

Example
https://opencellid.org/cell/get?key=xxx&mcc=260&mnc=2&lac=10250&cellid=26511
https://opencellid.org/cell/get?key=xxx&mcc=260&mnc=2&lac=10250&cellid=26511&radio=UMTS
https://opencellid.org/cell/get?key=xxx&mcc=260&mnc=2&lac=10250&cellid=26511&format=json

Availability
This feature is available free of charge in case your application contributes data to OpenCellID at the same time.
In case you want to use this service without contributing to OpenCellID please refer to the commercial users guideline.
An automated check for providing this service to white-listed apiKeys only was activated on Jun 15th, 2014

Getting the list of cells in a specified area
GET request to

https://<WebServiceURL>/cell/getInArea
Parameters
<WebServiceURL>: the URL to the web service

Payload
key=<apiKey>&BBOX=<latmin>,<lonmin>,<latmax>,<lonmax>&mcc=<mcc>&mnc=<mnc>&lac=<lac>&radio=<radio>&limit=<limit>&offset=<offset>&format=<format>

Where

Parameter	Data type	Description	Optional
<apiKey>	string	API key assigned to the user	no
<latmin>	double	Minimal bounding latitude	no
<lonmin>	double	Minimal bounding longitude	no
<latmax>	double	Maximal bounding latitude	no
<lonmax>	double	Maximal bounding longitude	no
<mcc>	integer	Mobile country code; If you want to restrict the result to a specific country	yes
<mnc>	integer	Mobile network code or system identifier; If you want to restrict the result	yes
<lac>	integer	Local area code, tracking area code or network id; If you want to restrict the result	yes
<radio>	string	You can specifiy GSM, UMTS, LTE, NR, NBIOT or CDMA as the radio of returned cells. Otherwise cells with any radios will be returned.	yes
<limit>	integer	A number defining maximum size of the returned list. Default and maximum value is 50. Use together with offset for paginating through large result sets.	yes
<offset>	integer	The number of items to skip before beginning to return results. Default is 0. Use together with limit for pagination (e.g., for page 2 with 20 items per page, use offset=20). Note: Since the database is updated minutely, the order of cells may change between API calls. For more consistent results when paginating through large datasets, consider using smaller bounding boxes when the getInAreaSize endpoint indicates a large number of cells.	yes
<format>	string	You can specify kml, xml, csv or json as output. Default is kml.
In csv type of output, there is a first line defining the content of the list.	yes
For the total number of cells in a specified area, please use /cell/getInAreaSize (described below).

Response
Upon successful request, HTTP 200 is returned with one of the following output structures:

XML
<?xml version="1.0" encoding="UTF-8"?>
<rsp stat="ok">
  <cell lat="52.9523755" 
        lon="9.252112" 
        mcc="262" 
        mnc="2" 
        lac="1434" 
        cellid="17275" 
        averageSignalStrength="-70" 
        range="123" 
        samples="12" 
        changeable="1" 
        radio="GSM"/>
  <cell lat="52.9463072" 
        lon="9.258809" 
        mcc="262" 
        mnc="2" 
        lac="1434" 
        cellid="17276" 
        averageSignalStrength="-98" 
        range="1234" 
        samples="11" 
        changeable="1" 
        radio="LTE" 
        tac="1434"/>
  <cell lat="52.9367185" 
        lon="9.27343925" 
        mcc="260" 
        mnc="16256" 
        lac="1434" 
        cellid="17272" 
        averageSignalStrength="-93" 
        range="12345" 
        samples="12" 
        changeable="1" 
        radio="CDMA" 
        sid="16256" 
        bid="1434" 
        nid="17272"/>
</rsp>
KML (default)
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://earth.google.com/kml/2.1">
  <Document>
    <name>OpenCellID Cells</name>
    <description>List of available cells</description>
    <Placemark>
      <name></name>
      <description><![CDATA[lat: <b>52.9523755</b><br/>
                            lon: <b>9.252112</b><br/>
                            mcc: <b>262</b><br/>
                            mnc: <b>2</b><br/>
                            lac: <b>1434</b><br/>
                            cellid: <b>17275</b><br/>
                            averageSignalStrength: <b>-73</b><br/>
                            range: <b>123</b><br/>
                            samples: <b>12</b><br/>
                            changeable: <b>1</b><br/>
                            radio: <b>GSM</b><br/>
                            rnc: <b></b><br/>
                            cid: <b></b><br/>
                            tac: <b></b><br/>
                            sid: <b></b><br/>
                            nid: <b></b><br/>
                            bid: <b></b>]]>
      </description>
      <Point><coordinates>9.252112,52.9523755,0</coordinates></Point>
    </Placemark>
    <Placemark>
      <name></name>
      <description><![CDATA[lat: <b>52.9463072</b><br/>
                            lon: <b>21.1525</b><br/>
                            mcc: <b>260</b><br/>
                            mnc: <b>2</b><br/>
                            lac: <b>1434</b><br/>
                            cellid: <b>17272</b><br/>
                            averageSignalStrength: <b>-83</b><br/>
                            range: <b>1234</b><br/>
                            samples: <b>11</b><br/>
                            changeable: <b>1</b><br/>
                            radio: <b>LTE</b><br/>
                            rnc: <b></b><br/>
                            cid: <b></b><br/>
                            tac: <b>1434</b><br/>
                            sid: <b></b><br/>
                            nid: <b></b><br/>
                            bid: <b></b>]]>
      </description>
      <Point><coordinates>21.1525,52.9463072,0</coordinates></Point>
    </Placemark>
    <Placemark>
      <name></name>
      <description><![CDATA[lat: <b>53.9367185</b><br/>
                            lon: <b>12.27343925</b><br/>
                            mcc: <b>262</b><br/>
                            mnc: <b>16256</b><br/>
                            lac: <b>1434</b><br/>
                            cellid: <b>17272</b><br/>
                            averageSignalStrength: <b>-91</b><br/>
                            range: <b>12345</b><br/>
                            samples: <b>2</b><br/>
                            changeable: <b>1</b><br/>
                            radio: <b>CDMA</b><br/>
                            rnc: <b></b><br/>
                            cid: <b></b><br/>
                            tac: <b></b><br/>
                            sid: <b>16256</b><br/>
                            nid: <b>1434</b><br/>
                            bid: <b>17272</b>]]>
      </description>
      <Point><coordinates>12.27343925,53.9367185,0</coordinates></Point>
    </Placemark>
    <Placemark>
      <name></name>
      <description><![CDATA[lat: <b>52.1322157</b><br/>
                            lon: <b>21.065345</b><br/>
                            mcc: <b>260</b><br/>
                            mnc: <b>2</b><br/>
                            lac: <b>1434</b><br/>
                            cellid: <b>42042781</b><br/>
                            averageSignalStrength: <b>-89</b><br/>
                            range: <b>1231</b><br/>
                            samples: <b>5</b><br/>
                            changeable: <b>1</b><br/>
                            radio: <b>UMTS</b><br/>
                            rnc: <b>641</b><br/>
                            cid: <b>34205</b><br/>
                            tac: <b></b><br/>
                            sid: <b></b><br/>
                            nid: <b></b><br/>
                            bid: <b></b>]]>
      </description>
      <Point><coordinates>21.065345,52.1322157,0</coordinates></Point>
    </Placemark>
  </Document>
</kml>
CSV
lat,lon,mcc,mnc,lac,cellid,averageSignalStrength,range,samples,changeable,radio,rnc,cid,tac,sid,nid,bid
52.275505,21.016382,260,2,45080,21728,-55,123,2,1,GSM,,,,,,
52.201454,21.065345,260,2,58140,42042781,-59,1234,3,1,UMTS,641,34205,,,,
52.207123,21.029972,260,6,58,119093,-80,122,2,1,LTE,,,58,,,
52.274612,21.034485,260,16256,45,22613,-98,134,2,1,CDMA,,,,16256,45,22613
JSON
{
  "count": 3,
  "cells": [
    {
      "lon": 21.014,
      "lat": 52.278,
      "mcc": 260,
      "mnc": 2,
      "lac": 45080,
      "cellid": 22613,
      "averageSignalStrength": -70,
      "range": 123,
      "samples": 2,
      "changeable": true,
      "radio": "GSM" 
    },
    {
      "lon": 21.04,
      "lat": 52.2234,
      "mcc": 260,
      "mnc": 2,
      "lac": 58140,
      "cellid": 42042781,
      "averageSignalStrength": -90,
      "range": 125,
      "samples": 2,
      "changeable": true,
      "radio": "UMTS",
      "rnc": 641,
      "cid": 34205
    },
    {
      "lon": 21.5604,
      "lat": 52.284,
      "mcc": 260,
      "mnc": 2,
      "lac": 581,
      "cellid": 42048,
      "averageSignalStrength": -90,
      "range": 12345,
      "samples": 9,
      "changeable": true,
      "radio": "LTE",
      "tac": 581
    }
  ]
}
Business logic
The request is handled by de.enaikoon.gpssuite.web.measure.CellController.

Example
https://www.opencellid.org/cell/getInArea?key=xxx&BBOX=52.0,21.0,52.5,21.5&mcc=260&mnc=2&lac=45070&limit=4&offset=16&format=kml
https://www.opencellid.org/cell/getInArea?key=xxx&BBOX=52.0,21.0,52.5,21.5&mcc=260&mnc=2&lac=45070&radio=CDMA&limit=4&offset=16&format=json

Availability
This feature is available free of charge for applications that contribute data to OpenCellID
Each cell returned counts as 1 API request credit. We recommend using the getInAreaSize endpoint first to check how many cells to expect before querying this endpoint
In case you want to use this service without contributing to OpenCellID please refer to the commercial users guideline.
Getting the number of cells in a specified area
GET request to

https://<WebServiceURL>/cell/getInAreaSize
Parameters
<WebServiceURL>: the URL to the web service

Payload
key=<apiKey>&BBOX=<latmin>,<lonmin>,<latmax>,<lonmax>&mcc=<mcc>&mnc=<mnc>&lac=<lac>&radio=<radio>&format=<format>

Where

Parameter	Data type	Description	Optional
<apiKey>	string	API key assigned to the user	no
<latmin>	double	Minimal bounding latitude	no
<lonmin>	double	Minimal bounding longitude	no
<latmax>	double	Maximal bounding latitude	no
<lonmax>	double	Maximal bounding longitude	no
<mcc>	integer	Mobile country code; If you want to restrict the result to a specific country	yes
<mnc>	integer	Mobile network code or system identifier; If you want to restrict the result	yes
<lac>	integer	Local area code, tracking area code or network id; If you want to restrict the result	yes
<format>	string	You can specifiy xml or json as output. Default is xml.	yes
Response
Upon successful request, HTTP 200 is returned with XML in the following format:

XML
<?xml version="1.0" encoding="UTF-8"?>
<rsp stat="ok">
  <cells count="5"/>
</rsp>
JSON
{
  "count": 123
}
Business logic
The request is handled by de.enaikoon.gpssuite.web.measure.CellController.

Example
https://www.opencellid.org/cell/getInAreaSize?key=xxx&BBOX=52.0,21.0,52.5,21.5&mcc=260&mnc=2&lac=45070
https://www.opencellid.org/cell/getInAreaSize?key=xxx&BBOX=52.0,21.0,52.5,21.5&mcc=260&mnc=2&lac=45070&format=json

Availability
Each API request counts as 2 API request credits.
This feature is available free of charge for applications that contribute data to OpenCellID
If you want to use this service without contributing to OpenCellID, please refer to the commercial users guideline