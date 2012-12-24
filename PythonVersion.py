#!/usr/bin/env python

# There is a complementary GLADE GTK file that is not included. You may build one up,
# using GtkToggleButtons for Digital Buttons with the "tooltip_text" field as the button ID
# and on_button_pressed/on_button_released as the pressed/released callbacks
#
# I also use a GtkVScale as my volume (analog). This is hardcoded.
#
# You will need to find all the thing between <<< >>> to run this application

import gtk
import socket
import re
import glib
import threading
import time

class CIPMessage:
  def __init__(self, msgType, body):
		self.type = msgType;
		self.length = len(body)
		self.payload = bytearray(map(lambda x: chr(x % 256), body))
	def create(self):
		msg = bytearray(3)
		msg[0] = self.type
		msg[1] = self.length >> 8
		msg[2] = self.length & 0xFF
		return msg + self.payload
		

class HeartbeatThread(threading.Thread):
	def __init__(self, parent):
		threading.Thread.__init__(self)
		self._stop = threading.Event()
		self.parent = parent
		self.counter = 0

	def stop(self):
		self._stop.set()

	def run(self):
		while 1:
			if self.parent.send_heartbeat() == 0:
				break
			while self.counter < 5 and not self._stop.isSet():
				time.sleep(1)
				self.counter += 1
			self.counter = 0
			if self._stop.isSet():
				break

class  HomeAutomationApp:
	button_mapping = {}

	def __init__( self ):
		self.builder = gtk.Builder()
		self.builder.add_from_file("<<< Path To Glade File >>>")
		self.window = self.builder.get_object ("<<< GtkWindow ID >>>")
		if self.window:
			self.window.connect("destroy", gtk.main_quit)
		for i in self.builder.get_objects():
			if type(i) is gtk.ToggleButton:
				if i.get_has_tooltip():
					self.button_mapping[i.get_tooltip_text()] = i
		self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
		self.sock.connect(("<<< IP Address >>>", 41794))
		self.builder.connect_signals(self)
		self.sock.settimeout(.1)
		self.heartbeat = HeartbeatThread(self)
		self.heartbeat.start()
		while 1:
			try:
				self.recie
			except:
				break
		glib.io_add_watch(self.sock, glib.IO_IN, self.receive_data)
		
	def send_message(self, cip):
		try:
			sent = self.sock.send(cip.create())
			return sent
		except:
			return 0

	def send_buttonpress(self, join):
		self.send_message(CIPMessage(0x05, [0x00, 0x00, 0x03, 0x27, join & 0xFF, join >> 8]))
                 
	def send_buttonrelease(self, join):
		self.send_message(CIPMessage(0x05, [0x00, 0x00, 0x03, 0x27, join & 0xFF, (join >> 8) | 0x80]))

	def <<< Callback for when destroyed >>>(self, widget):
		self.heartbeat.stop()
		self.sock.shutdown(2)
		
	def on_button_pressed(self, widget):
		self.send_buttonpress(int(widget.get_tooltip_text())-1)

	def on_button_released(self, widget):
		self.send_buttonrelease(int(widget.get_tooltip_text())-1)

	def on_volume_change_value(self, widget, scroll, v):
		join = 0
		value = int(v)
		self.send_message(CIPMessage(0x05, [0x00, 0x00, 0x05, 0x14, join >> 8, join & 0xFF, value >> 8, value & 0xFF]))

	def send_heartbeat(self):
		return self.send_message(CIPMessage(0x0D, [0x00, 0x00]))

	def send_updaterequest(self):
		return self.send_message(CIPMessage(0x05, [0x00, 0x00, 0x02, 0x03, 0x00]))

	def handle_feedback(self, cip):
		if cip.type == 0x02: # IP registration
			if cip.payload[0] == 0xFF and cip.payload[1] == 0xFF and cip.payload[2] == 0x02:
				raise RuntimeError("Crestron ID bad")
			elif cip.length == 4:
				self.send_heartbeat()
				self.send_updaterequest()
		elif cip.type == 0x05: # Data
			self.handle_data(cip)
		elif cip.type == 0x03: # Program stop or disconnect
			pass 
		elif cip.type == 0x0D: # Heartbeat disconnect
			pass 
		elif cip.type == 0x0E: # Heartbeat ack
			pass
		elif cip.type == 0x0F:
			if cip.length == 1 and cip.payload[0] == 0x02:
				self.send_message(CIPMessage(0x01, [0x7F, 0x00, 0x00, 0x01, 0x00, 0x04 , 0x40])) #0x04 is the ID
			else:
				raise RuntimeError("Bad registration")


	def handle_data(self, cip):
		value = 0
		join = 0
		jType = cip.payload[3]
		if jType == 0x00: # Digital
			value = 0
			if cip.payload[5] & 0x80 == 0:
				value = 1
			join = cip.payload[5] & 0x7F
			join = (join << 8) + (cip.payload[4] & 0xFF) + 1
		elif jType == 0x01: # Analog
			if cip.payload[2] == 0x04: # Join < 256
		        	join = (cip.payload[4] & 0xFF) + 1
		        	value = cip.payload[5] & 0x00FF
				value = (value << 8) + (cip.payload[6] & 0xFF)
			elif cip.payload[2] == 0x05:
				join = cip.payload[4] & 0xFF
				join = (join << 8) + (cip.payload[5] & 0xFF) + 1
		        	value = cip.payload[6] & 0x00FF
				value = (value << 8) + (cip.payload[7] & 0xFF)
		elif jType == 0x02: # Serial
			pass
		elif jType == 0x03: # Update request confirmation
			pass

# if you have custom join behavior, it goes in here
		if jType == 0x00:
			try:
				obj = self.button_mapping[join]
			except:
				obj = None
			if obj != None:
				obj.set_active(value)
				obj.queue_draw()
		elif jType == 0x01:
			if join == 1 : # volume
				self.builder.get_object("volume").set_value(float(value))

	def receive_data(self, arg1, arg2):
		buf = bytearray(b" " * 1024)
		rx = self.sock.recv_into(buf)
		index = 0
		while index < rx:
			t = buf[index]
			l = buf[index + 1] << 8
			l += buf[index + 2]
			index += 3
			self.handle_feedback(CIPMessage(t,buf[index:index+l]))
			index += l
		return True

settings = gtk.settings_get_default()
settings.props.gtk_button_images = True
settings.props.gtk_enable_tooltips = False
app = HomeAutomationApp()
app.window.show()
gtk.main()



