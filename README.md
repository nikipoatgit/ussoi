# ussoi ( !! Note: App under development; limited functionality )
UART Soft Serial Over Internet (USSOI) – tunnels UART serial communication over the internet.

<p align="center">
  <img src="doc/ussoi_flow_chart.png" alt="UART Soft Serial Over Internet Flow Chart" width="600"/>
</p>



## Libraries Used

### [Android-Bluetooth-Library](https://github.com/prasad-psp/Android-Bluetooth-Library.git)  
*Reason:* Simplifies Bluetooth Classic communication on Android, providing stable connections and easy device discovery. also host ( phone ) can be charged as OTG is not being used.

### [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android)  
*Reason:* Offers reliable USB-to-serial support for a wide range of chipsets, making it easy to talk to UART devices via USB OTG.

---
## Data Format

### Endpoints

- **Receive data from USSOI:**   `<url>`

- **Send data to USSOI (Downlink):**   `<url>/downlink/`  

> Requests sent to the downlink endpoint are **long-polled** and will be held for up to **30 seconds** before timing out.


### Recived Data From Ussoi
```json
{
  "mavlink_out": "<base64_encoded_data>",
  "config": "<config_object>",
  "encoding": "base64"
}
```
### Transmitting Data Fro Ussoi
```json
{
   "b64":"<base64_encoded_data>"
}
```
---
## Transport characteristics ( Tested on :  Flight controller <-> Mission Planner )

- **HTTP (long-polling / downlink)**  
  - Use when the server must hold requests (long-poll) at `<url>/downlink/`.  
  - **Typical packet loss:** **10–40%** (high).  
  - **Notes:** higher overhead and higher loss rates due to repeated connection setup and long-poll timeouts.

- **WebSocket**  
  - **Typical packet loss:** **1–5%** (low).  



## How to Use

data dent via http (due to pooling ) packet loss is high and upto 30~50%

while with websocket it is 5~1%


### USB Mode

1. **Connect the UART device** via USB-OTG.

2. Confirm the device appears in the **Info** section.  

3. Make sure  **Bluetooth** is disabled for USB-OTG mode .

4. Enter the desired **baud rate**.

5. Enter the target **IP address** ( Note url ends with /)
 
   <img src="doc\ussoi_para_select.jpg" alt="HTTP Mode Screen" width="400"/>
   

## Modify Source Code

1. **Clone the repository** into your local machine (or directly in Android Studio):  
   ```bash
   git clone https://github.com/nikipoatgit/ussoi.git
