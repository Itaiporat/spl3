# SPL241 Assignment 3 - Extended TFTP Server and Client

## Overview
This project is an implementation of an extended **Trivial File Transfer Protocol (TFTP)** server and client. The TFTP server allows multiple users to upload, download, and delete files while broadcasting file changes to all connected clients. The communication between the server and clients follows a **binary protocol** over TCP.

## Features
- **Client**
  - User authentication via login command.
  - File upload (WRQ) and download (RRQ).
  - File deletion (DELRQ) with server acknowledgment.
  - Directory listing request (DIRQ) to view available files.
  - Broadcast messages (BCAST) for file additions and deletions.
  - Disconnection (DISC) to exit the server properly.

- **Server**
  - Supports multiple clients using the **Thread-Per-Client (TPC)** model.
  - Manages file operations with access control.
  - Implements the **Connections** interface to handle active clients.
  - Encodes and decodes binary data using **Big-Endian format**.
  - Processes packets including LOGRQ, WRQ, RRQ, DELRQ, DIRQ, DISC, DATA, ACK, BCAST, and ERROR.

## Project Structure
```
project_root/
│── client/
│   ├── src/
│   ├── pom.xml  # Maven build file for client
│── server/
│   ├── src/
│   ├── Files/   # Server storage for uploaded files
│   ├── pom.xml  # Maven build file for server
│── README.md
```

## Installation
1. **Clone the repository:**
   ```sh
   git clone <repository_url>
   cd project_root
   ```

2. **Build the project using Maven:**
   ```sh
   cd server
   mvn compile
   cd ../client
   mvn compile
   ```

## Running the Server & Client

### Starting the Server
Navigate to the `server/` directory and run:
```sh
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpServer" -Dexec.args="<port>"
```
Example:
```sh
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpServer" -Dexec.args="7777"
```

### Starting the Client
Navigate to the `client/` directory and run:
```sh
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpClient" -Dexec.args="<ip> <port>"
```
Example:
```sh
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpClient" -Dexec.args="127.0.0.1 7777"
```

## Client Commands
The client accepts commands via the terminal:

| Command  | Description |
|----------|-------------|
| `LOGRQ <username>` | Log in with a username |
| `WRQ <filename>` | Upload a file to the server |
| `RRQ <filename>` | Download a file from the server |
| `DELRQ <filename>` | Delete a file from the server |
| `DIRQ` | Request a list of all files on the server |
| `DISC` | Disconnect from the server |

## Example Usage
### 1. Login and Download a File
```
> LOGRQ Alice
ACK 0
> RRQ sample.txt
ACK 1
ACK 2
RRQ sample.txt complete
> DISC
ACK 0
```

### 2. Upload a File
```
> LOGRQ Bob
ACK 0
> WRQ document.pdf
ACK 0
ACK 1
ACK 2
WRQ document.pdf complete
> DISC
ACK 0
```

## Error Handling
The server returns error codes for invalid operations:
| Error Code | Description |
|------------|-------------|
| 1 | File not found |
| 2 | Access violation |
| 3 | Disk full |
| 4 | Illegal TFTP operation |
| 5 | File already exists |
| 6 | User not logged in |
| 7 | User already logged in |

## Submission Guidelines
- The final submission should be a **ZIP file** structured as follows:
```
student1ID_student2ID.zip
│── client/
│   ├── src/
│   ├── pom.xml
│── server/
│   ├── src/
│   ├── Files/
│   ├── pom.xml
```
- Ensure all commands work before submission using:
  ```sh
  mvn compile
  mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpServer" -Dexec.args="127.0.0.1 7777"
  ```

## Authors
- **Hedi Zisling**
- **Or Kadosh**

## License
This project is for academic purposes under SPL241 and follows university submission policies.

