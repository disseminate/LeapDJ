import java.io.IOException;
import java.lang.Math;
import com.leapmotion.leap.*;
import com.leapmotion.leap.Gesture.State;
import javax.sound.midi.*;
import java.util.List;
import java.util.ArrayList;

class DJListener extends Listener {
	private MidiDevice out;
	private Receiver rec;
	private Frame lastFrame;

	public void onInit( Controller ctrl ) {

	}
	public void onConnect( Controller ctrl ) {
		System.out.println( "Connected to LeapMotion.");
		System.out.println( "Connecting MIDI Device..." );

		MidiDevice.Info[] info = MidiSystem.getMidiDeviceInfo();
		for( int i = 0; i < info.length; i++ ) {
			if( info[i].getName().equals( "LeapDJ" ) ) {
				if( info[i].getDescription().equals( "External MIDI Port" ) ) { // Receiver
					try {
						out = MidiSystem.getMidiDevice( info[i] );
						out.open();
						rec = out.getReceiver();
						System.out.println( "Connected! Press enter to exit." );
					} catch( MidiUnavailableException e ) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	public void onDisconnect( Controller ctrl ) {
		System.out.println( "Disconnected from LeapMotion." );
		if( out != null ) {
			out.close();
		}
		if( rec != null ) {
			rec.close();
		}
	}
	public void onExit( Controller ctrl ) {
		if( out != null ) {
			out.close();
		}
		if( rec != null ) {
			rec.close();
		}
	}

	private void sendMidiMessage( int type, int cmd, int chan, int val ) {
		try {
			ShortMessage shortMessage = new ShortMessage();
			shortMessage.setMessage(type, cmd, chan, val);
			rec.send( shortMessage, out.getMicrosecondPosition() );
		} catch( InvalidMidiDataException e ) {
			e.printStackTrace();
		}
	}

	private float clamp( float in, float min, float max ) {
		if( in > max )
			return max;

		if( in < min )
			return min;

		return in;
	}

	private int clamp( int in, int min, int max ) {
		if( in > max )
			return max;

		if( in < min )
			return min;

		return in;
	}

	public boolean isHandExtended( Hand h ) {
		FingerList extendedFingerList = h.fingers().extended();
		if( extendedFingerList.count() < 5 ) {
			return false;
		}
		return true;
	}

	public void setMidi( int chan, int m, int val ) {
		sendMidiMessage( ShortMessage.CONTROL_CHANGE, chan, m, val );
	}

	public boolean isFingerSolo( Hand h, Finger f ) {
		if( f.isValid() && f.isExtended() && h.fingers().extended().count() == 1 ) {
			return true;
		}

		return false;
	}

	public float getPointingPosition( Hand h, Finger f ) {
		if( f.isValid() ) {
			Vector fPos = f.stabilizedTipPosition();
			float x = fPos.getX();

			if( x < 0 )
				x *= -1;

			return x;
		}
		return 0;
	}

	public void onFrameHand( Controller ctrl, Hand h, int chan ) {
		float sphereRad = h.sphereRadius();

		float pitch = h.direction().pitch();
		float yaw = h.direction().yaw();
		float roll = h.direction().roll();

		if( roll < 0 ) {
			roll = (2 * 3.14159f) - (roll * -1);
		}
		roll = roll / (3.14159f);

		if( isHandExtended( h ) ) {
			if( sphereRad > 45 ) {
				int pitchMidi = clamp( (int)(((pitch+1)/2)*127), 0, 127 );
				sendMidiMessage( ShortMessage.CONTROL_CHANGE, chan, 0, pitchMidi );
				
				int rollMidi = clamp( (int)((roll / 2) * 127), 0, 127 );
				//sendMidiMessage( ShortMessage.CONTROL_CHANGE, chan, 1, rollMidi );
			} else {
				setMidi( chan, 0, 63 );
			}
		} else if( isFingerSolo( h, h.fingers().frontmost() ) ) {
			float f = getPointingPosition( h, h.fingers().frontmost() );
			int fingerMidi = clamp( (int)((f / 100) * 127), 0, 127 );
			sendMidiMessage( ShortMessage.CONTROL_CHANGE, chan, 1, fingerMidi );
		} else {
			setMidi( chan, 0, 63 );
			setMidi( chan, 1, 0 );
		}
	}

	private long turnOffTurntableLeft = -1;
	private long turnOffTurntableRight= -1;

	public void handleKeyTap( KeyTapGesture g ) {
		HandList hands = g.hands();

		for( int i = 0; i < hands.count(); i++ ) {
			if( hands.get( i ).isLeft() ) {
				turnOffTurntableLeft = g.frame().timestamp() + (long)(0.5 * 1000000);
			} else {
				turnOffTurntableRight = g.frame().timestamp() + (long)(0.5 * 1000000);
			}
		}
	}
	
	private void handleTimers( Frame f ) {
		if( turnOffTurntableLeft > -1 ) {
			if( f.timestamp() > turnOffTurntableLeft ) {
				sendMidiMessage( ShortMessage.CONTROL_CHANGE, 1, 3, 0 );
				sendMidiMessage( ShortMessage.CONTROL_CHANGE, 1, 2, 0 );
				turnOffTurntableLeft = -1;
			} else {
				sendMidiMessage( ShortMessage.CONTROL_CHANGE, 1, 2, 1 );
				sendMidiMessage( ShortMessage.CONTROL_CHANGE, 1, 3, 127 );
			}
		}
		if( turnOffTurntableRight > -1 ) {
			if( f.timestamp() > turnOffTurntableRight ) {
				sendMidiMessage( ShortMessage.CONTROL_CHANGE, 2, 3, 0 );
				sendMidiMessage( ShortMessage.CONTROL_CHANGE, 2, 2, 0 );
				turnOffTurntableRight = -1;
			} else {
				sendMidiMessage( ShortMessage.CONTROL_CHANGE, 2, 2, 1 );
				sendMidiMessage( ShortMessage.CONTROL_CHANGE, 2, 3, 127 );
			}
		}
	}

	public void onFrame( Controller ctrl ) {
		Frame frame = ctrl.frame();

		handleTimers( frame );

		HandList hands = frame.hands();

		if( hands.count() > 0 ) {
			Hand left = hands.leftmost();
			onFrameHand( ctrl, left, 0 );
			if( hands.count() > 1 ) {
				Hand right = hands.rightmost();
				onFrameHand( ctrl, right, 1 );
			}
		} else {
			setMidi( 1, 0, 63 );
			setMidi( 2, 0, 63 );
			setMidi( 1, 1, 0 );
			setMidi( 2, 1, 0 );
		}

		GestureList gesturesInFrame = frame.gestures();

		if( gesturesInFrame.count() > 0 ) {
			for( int i = 0; i < gesturesInFrame.count(); i++ ) {
				Gesture g = gesturesInFrame.get( i );
				if( g.type() == Gesture.Type.TYPE_KEY_TAP ) {
					handleKeyTap( new KeyTapGesture( g ) );
				}
			}
		}

		lastFrame = frame;
	}
}

class LeapDJ {
	public static void main( String[] args ) {
		DJListener listen = new DJListener();
		Controller ctrl = new Controller();
		ctrl.setPolicy(Controller.PolicyFlag.POLICY_BACKGROUND_FRAMES);
		ctrl.enableGesture(Gesture.Type.TYPE_KEY_TAP);

		ctrl.addListener( listen );

		try {
			System.in.read();
		} catch( IOException e ) {
			e.printStackTrace();
		}

		ctrl.removeListener( listen );
	}
}