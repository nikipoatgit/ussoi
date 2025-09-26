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
## How to Use

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
