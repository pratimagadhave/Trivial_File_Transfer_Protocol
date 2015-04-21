/**
 * Connect to glados.cs.rit.edu tftp deamon server
 * and perform get file operations.
 * Handled - File Not Found and Time Out
 * 
 * @author Pratima Gadhave
 *
 * @version 1.0
 */

import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.StringTokenizer;

public class ClientOption2 {
	int connect = 0;
	int infile = 0;
	InetAddress address = null;
	int port = 0;

	// constant variables for commands
	private static final String CONNECT = "connect";
	private static final String GET = "get";
	private static final String QUIT = "quit";
	private static final String OTHER = "?Invalid Argument";

	// initial port of connection to the server
	private static final int INITIAL_PORT = 69;

	// sending packet RRQ request
	byte[] sendPacket = null;

	/**
	 * Connect method to connect the server.
	 * 
	 * @param ipAddress
	 */
	private void Connect(String ipAddress) {
		try {
			address = InetAddress.getByName(ipAddress);
			connect = 1;
			// System.out.println("Connected to " + address);
		} catch (UnknownHostException e) {
			// if the host server does not exists
			System.out.println(ipAddress + "Unknown host");
		}
	}

	/**
	 * get method to get the files on the server to the local machine.
	 */
	private void Get() {
		// create a UDP client socket
		DatagramSocket client = null;
		try {
			client = new DatagramSocket();
			// set the time out for connecting to the server to 5sec
			client.setSoTimeout(5000);

			Scanner getInput = new Scanner(System.in);
			// get the filename from the user
			System.out.print("(file) ");
			String getFile = getInput.next();

			// set the opcode to 01 for RRQ
			byte[] opcodeGet = new byte[2];
			opcodeGet[0] = (byte) 0x00;
			opcodeGet[1] = (byte) 0x01;

			// get the exact file name from the path given.
			StringTokenizer parts = new StringTokenizer(getFile, "/");
			String exactFile = null;
			while (parts.hasMoreTokens()) {
				exactFile = parts.nextElement().toString();
			}
			// convert the filename into bytes for transfer
			byte[] fileInBytes = exactFile.getBytes();

			// mark the end of filename
			byte[] endFile = new byte[1];
			endFile[0] = (byte) 0x00;

			// set the mode as "octet"
			String modeSelect = "octet";
			byte[] mode = modeSelect.getBytes();

			// mark the end of the packet to send
			byte[] endPacket = new byte[1];
			endPacket[0] = (byte) 0x00;

			// combine all the bytes in one packet for sending
			sendPacket = new byte[4 + fileInBytes.length + mode.length];

			int position = 0;

			for (int index = 0; index < opcodeGet.length; index++) {
				sendPacket[position++] = opcodeGet[index];
			}

			for (int index = 0; index < fileInBytes.length; index++) {
				sendPacket[position++] = fileInBytes[index];
			}

			sendPacket[position++] = endFile[0];

			for (int index = 0; index < mode.length; index++) {
				sendPacket[position++] = mode[index];
			}

			sendPacket[position] = endPacket[0];

			// send the RRQ request to get the file
			DatagramPacket sendData = new DatagramPacket(sendPacket,
					sendPacket.length, address, INITIAL_PORT);
			client.send(sendData);

			// receive of file in 512 blocks so loop
			while (true) {
				// set receiver buffer
				byte[] receiveBuffer = new byte[516];

				// get the data from the server
				DatagramPacket receiveData = new DatagramPacket(receiveBuffer,
						receiveBuffer.length);
				client.receive(receiveData);

				// get the destination port for sending the acknowledgments
				port = receiveData.getPort();

				// create a acknowledgement packet
				byte[] ackSend = new byte[4];
				ackSend[0] = (byte) 0x00;
				ackSend[1] = (byte) 0x04;
				ackSend[2] = (byte) -1;
				ackSend[3] = (byte) -1;

				// check if we getting new packet and not the previous packet;
				// this might happen if ack for previous packet was lost
				if (receiveBuffer[1] == 3 && ackSend[2] == (byte) -1
						&& ackSend[3] == (byte) -1) {
					byte[] fileReceived = new byte[receiveData.getLength() - 4];

					// get the file in to the buffer
					for (int index = 0; index < (receiveData.getLength() - 4); index++) {
						fileReceived[index] = receiveBuffer[index + 4];
					}

					// write the file; if file already exists we append, else we
					// create a new file
					FileOutputStream fileOut = null;
					if (infile == 1) {
						fileOut = new FileOutputStream(exactFile, true);
					} else if (infile == 0) {
						fileOut = new FileOutputStream(exactFile);
						infile = 1;
					}
					fileOut.write(fileReceived);
					fileOut.close();

					// sending the acknowledgement back
					if (receiveData.getLength() >= 516) {
						ackSend[2] = receiveData.getData()[2];
						ackSend[3] = receiveData.getData()[3];

						// send ack packet
						DatagramPacket sendAck = new DatagramPacket(ackSend,
								ackSend.length, address, port);
						client.send(sendAck);

						// reset buffer
						receiveBuffer = new byte[516];
						ackSend[2] = (byte) -1;
						ackSend[3] = (byte) -1;
					}
					// in case we are done with the file download
					else if (receiveData.getLength() < 516) {
						System.out.println("Complete");
						client.close();
						break;
					}
				}
				// check if server is resending the packet; if so retransmit the
				// ack since it did not reach server.
				else if (receiveBuffer[1] == 3
						&& ackSend[2] == receiveData.getData()[2]
						&& ackSend[3] == receiveData.getData()[3]) {
					DatagramPacket sendAck = new DatagramPacket(ackSend,
							ackSend.length, address, port);
					client.send(sendAck);
				}
				// check for error of file not found
				else if (receiveBuffer[3] == 1 && receiveBuffer[1] == 5) {
					System.out.println("File does not exist");
					client.close();
					break;
				}
			}
			client.close();
		} catch (SocketTimeoutException e) {
			System.out.println("Transfer Time out");
			connect = 0;
			client.close();
		} catch (Exception e) {

		}
	}

	public static void main(String[] args) {
		ClientOption2 client = new ClientOption2();
		Scanner input = new Scanner(System.in);
		// run the loop until user quits and act as tftp control
		while (true) {
			System.out.print("tftp> ");
			String value = input.next();
			if (value.equalsIgnoreCase(CONNECT) && client.connect == 0) {
				System.out.print("(to) ");
				String to = input.next();
				client.Connect(to);
			} else if (value.equalsIgnoreCase(GET) && client.connect == 0) {
				System.out.println("Not yet connected");
			} else if (value.equalsIgnoreCase(GET) && client.connect == 1) {
				client.Get();
			} else if (value.equalsIgnoreCase(QUIT)) {
				input.close();
				System.exit(0);
			} else if (value.equalsIgnoreCase(CONNECT) && client.connect == 1) {
				System.out.print("(to) ");
				String to = input.next();
				client.Connect(to);
			} else {
				System.out.println(OTHER);
			}
		}
	}

}
