package bit.datacron.linkedIn.tms.gui;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import com.googlecode.lanterna.Symbols;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.TextColor.ANSI;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

import bit.datacron.linkedIn.tms.system.TemperatureMonitoringSystem;

public class LanterminalEngine {
	private final TemperatureMonitoringSystem tMS;
	private Screen screen;
	private TextColor foreC;
	private TextColor backC;
	private boolean isOn;
	private String[] locationDescriptions;
	private double tempThreshold = 50.0;
	private final int[] posX = {3, 8, 25, 70};
	private final String[] labels = {"#", "LOCATION", "TEMPERATURE", "STATUS"};

	private JFrame swingFrame;
	private DefaultTableModel tableModel;

	public LanterminalEngine(TemperatureMonitoringSystem tMS) {
		this.tMS = tMS;
		try {
			initialize();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to initialize terminal engine", e);
		}
	}

	private void initialize() throws IOException {
		try {
			DefaultTerminalFactory factory = new DefaultTerminalFactory();
			factory.setPreferTerminalEmulator(true);
			factory.setTerminalEmulatorTitle("SSI TERMINAL v1.0");
			screen = factory.createScreen();
			foreC = TextColor.ANSI.RED;
			backC = TextColor.ANSI.BLACK;
		} catch (IOException | RuntimeException e) {
			screen = null;
			foreC = TextColor.ANSI.RED;
			backC = TextColor.ANSI.BLACK;
			System.err.println("[LanterminalEngine] Warning: cannot create Lanterna screen, falling back to Swing GUI. Reason: " + e.getMessage());
		}
	}

	public void turnOn() throws IOException {
		if (screen == null) {
			displayDataSwing();
			return;
		}

		screen.startScreen();
		setup();
		drawUI("  SSI TERMINAL v1.0 ", "  F10: Quit  ");
		isOn = true;
		displayData();
		screen.stopScreen();
	}

	public void turnOff() throws IOException {
		isOn = false;
		if (screen != null) {
			screen.stopScreen();
		}
		if (swingFrame != null) {
			swingFrame.dispose();
		}
	}

	private void displayDataSwing() {
		isOn = true;
		SwingUtilities.invokeLater(() -> {
			swingFrame = new JFrame("SSI TERMINAL v1.0");
			tableModel = new DefaultTableModel(new Object[]{"#", "LOCATION", "TEMPERATURE", "STATUS"}, 0) {
				@Override
				public boolean isCellEditable(int row, int column) {
					return false;
				}
			};
			JTable table = new JTable(tableModel);
			swingFrame.add(new JScrollPane(table));
			swingFrame.setSize(700, 400);
			swingFrame.setLocationRelativeTo(null);
			swingFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			swingFrame.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosed(WindowEvent e) {
					isOn = false;
				}
			});
			swingFrame.setVisible(true);
		});

		Thread updater = new Thread(new Runnable() {
			@Override
			public void run() {
				while (isOn) {
					final double[] temps = tMS.getTemperatures();
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							updateSwingTable(temps);
						}
					});
					try {
						Thread.sleep(800);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						if (swingFrame != null) {
							swingFrame.dispose();
						}
					}
				});
			}
		}, "Lanterminal-Swing-Updater");
		updater.setDaemon(true);
		updater.start();
	}

	private void updateSwingTable(double[] temps) {
		if (tableModel == null) {
			return;
		}
		if (tableModel.getRowCount() != temps.length) {
			tableModel.setRowCount(0);
			for (int i = 0; i < temps.length; i++) {
				tableModel.addRow(new Object[]{
					i + 1,
					locationLabel(i),
					String.format("%.1f", temps[i]),
					statusForTemp(temps[i])
				});
			}
		} else {
			for (int i = 0; i < temps.length; i++) {
				tableModel.setValueAt(String.format("%.1f", temps[i]), i, 2);
				tableModel.setValueAt(statusForTemp(temps[i]), i, 3);
			}
		}
	}

	private String locationLabel(int index) {
		if (locationDescriptions != null && index < locationDescriptions.length) {
			return locationDescriptions[index];
		}
		return "Sensor" + (index + 1);
	}

	private String statusForTemp(double temp) {
		if (temp < 10) {
			return "COLD";
		}
		if (temp < 20) {
			return "COOL";
		}
		if (temp < 30) {
			return "NORMAL";
		}
		if (temp < 35) {
			return "WARM";
		}
		return "HOT";
	}

	public void drawUI(String header, String footer) throws IOException {
		screen.setCursorPosition(null);

		int width = screen.getTerminalSize().getColumns() - 1;
		int height = screen.getTerminalSize().getRows() - 1;

		screen.clear();
		screen.newTextGraphics().drawLine(0, 0, width, 0, new TextCharacter(' ')
				.withBackgroundColor(foreC).withForegroundColor(backC));
		screen.newTextGraphics().drawLine(0, height, width, height, new TextCharacter(' ')
				.withBackgroundColor(foreC).withForegroundColor(backC));
		screen.newTextGraphics().setBackgroundColor(foreC).setForegroundColor(backC)
				.putCSIStyledString(0, 0, header);
		screen.newTextGraphics().setBackgroundColor(foreC).setForegroundColor(backC)
				.putCSIStyledString(0, height, footer);

		Map<Integer, String> colSetup = new HashMap<>();
		for (int i = 0; i < posX.length; i++) {
			colSetup.put(posX[i], labels[i]);
		}

		int row = 2;
		colSetup.forEach((k, v) -> screen.newTextGraphics().putString(k, row, v));

		screen.newTextGraphics().setForegroundColor(ANSI.YELLOW).setCharacter(0, 1, Symbols.DOUBLE_LINE_TOP_LEFT_CORNER);
		screen.newTextGraphics().drawLine(1, 1, width - 1, 1, new TextCharacter(Symbols.DOUBLE_LINE_HORIZONTAL)
				.withForegroundColor(ANSI.YELLOW));
		screen.newTextGraphics().setForegroundColor(ANSI.YELLOW).setCharacter(width, 1, Symbols.DOUBLE_LINE_TOP_RIGHT_CORNER);
		screen.newTextGraphics().setForegroundColor(ANSI.YELLOW).setCharacter(0, 2, Symbols.DOUBLE_LINE_VERTICAL);
		screen.newTextGraphics().setForegroundColor(ANSI.YELLOW).setCharacter(width, 2, Symbols.DOUBLE_LINE_VERTICAL);
		screen.newTextGraphics().setForegroundColor(ANSI.YELLOW).setCharacter(0, 3, Symbols.DOUBLE_LINE_T_RIGHT);
		screen.newTextGraphics().drawLine(1, 3, width - 1, 3, new TextCharacter(Symbols.DOUBLE_LINE_HORIZONTAL)
				.withForegroundColor(ANSI.YELLOW));
		screen.newTextGraphics().setForegroundColor(ANSI.YELLOW).setCharacter(width, 3, Symbols.DOUBLE_LINE_T_LEFT);
		screen.newTextGraphics().drawLine(0, 4, 0, height - 3, new TextCharacter(Symbols.DOUBLE_LINE_VERTICAL)
				.withForegroundColor(ANSI.YELLOW));
		screen.newTextGraphics().drawLine(width, 4, width, height - 3, new TextCharacter(Symbols.DOUBLE_LINE_VERTICAL)
				.withForegroundColor(ANSI.YELLOW));
		screen.newTextGraphics().setForegroundColor(ANSI.YELLOW).setCharacter(0, height - 2, Symbols.DOUBLE_LINE_BOTTOM_LEFT_CORNER);
		screen.newTextGraphics().drawLine(1, height - 2, width - 1, height - 2, new TextCharacter(Symbols.DOUBLE_LINE_HORIZONTAL)
				.withForegroundColor(ANSI.YELLOW));
		screen.newTextGraphics().setForegroundColor(ANSI.YELLOW).setCharacter(width, height - 2, Symbols.DOUBLE_LINE_BOTTOM_RIGHT_CORNER);
		screen.newTextGraphics().putString(posX[0], height - 1, "Highest Temperature Record: ");
		screen.refresh();
	}

	private void displayData() throws IOException {
		int rowDefault = 2;
		int rowOffset = 2;
		DecimalFormat decimalFormat = new DecimalFormat("#.#");

		Map<Character, Integer> tBarBorders = new HashMap<>();
		char markIn = '[';
		char markOut = ']';
		tBarBorders.put(markIn, posX[2] + 5);
		tBarBorders.put(markOut, posX[3] - 3);
		int barIn = tBarBorders.get(markIn) + 1;
		int maxBarOut = tBarBorders.get(markOut) - 1;
		int maxBarLength = maxBarOut - barIn;
		char barSpacer = Symbols.BLOCK_SPARSE;
		char barFiller = Symbols.BLOCK_SOLID;

		Map<String, TextColor> statusMap = new HashMap<>();
		statusMap.put("COLD", ANSI.WHITE);
		statusMap.put("COOL", ANSI.CYAN);
		statusMap.put("NORMAL", ANSI.GREEN);
		statusMap.put("WARM", ANSI.YELLOW);
		statusMap.put("HOT", ANSI.RED);

		Double highestTemp = 0.0;
		int highestTempIndex = 0;

		while (isOn) {
			KeyStroke keyStroke = screen.pollInput();
			if (keyStroke != null && keyStroke.getKeyType() == KeyType.F10) {
				screen.clear();
				cursorWait(0, 2, 666);
				typeln(">_ SYSTEM EXIT HOTKEY ON", 0, 0);
				cursorWait(0, 2, 1111);
				typeln(">_ SESSION TERMINATED", 0, 1);
				cursorWait(0, 2, 1111);
				break;
			}

			int row = rowDefault;
			double[] temperatures = tMS.getTemperatures();
			for (int i = 0; i < temperatures.length; i++) {
				row += rowOffset;
				double temp = temperatures[i];
				if (highestTemp < temp) {
					highestTemp = temp;
					highestTempIndex = i;
				}

				screen.newTextGraphics().setForegroundColor(foreC).putCSIStyledString(posX[0], row, Integer.toString(i + 1));
				screen.newTextGraphics().setForegroundColor(ANSI.WHITE).putCSIStyledString(posX[1], row, locationLabel(i));
				screen.newTextGraphics().setForegroundColor(ANSI.GREEN).putCSIStyledString(posX[2], row, decimalFormat.format(temp));

				screen.setCharacter(tBarBorders.get(markIn), row, new TextCharacter(markIn));
				screen.newTextGraphics().drawLine(barIn, row, maxBarOut, row, new TextCharacter(barSpacer).withForegroundColor(foreC));
				screen.setCharacter(tBarBorders.get(markOut), row, new TextCharacter(markOut));

				int barLength = (int) ((temp * maxBarLength) / tempThreshold);
				int barOut = Math.max(barIn, Math.min(maxBarOut, barLength + barIn));
				screen.newTextGraphics().drawLine(barIn, row, barOut, row, new TextCharacter(barFiller).withForegroundColor(foreC));

				String status = statusForTemp(temp);
				screen.newTextGraphics().drawLine(posX[3], row, posX[3] + 6, row, new TextCharacter(' ').withForegroundColor(foreC));
				screen.newTextGraphics().setForegroundColor(statusMap.get(status)).putCSIStyledString(posX[3], row, status);

				if (highestTemp.equals(temp)) {
					int height = screen.getTerminalSize().getRows() - 1;
					screen.newTextGraphics().drawLine(posX[2] + 6, height - 1, screen.getTerminalSize().getColumns() - 2, height - 1, ' ');
					screen.newTextGraphics().setForegroundColor(ANSI.YELLOW)
						.putString(posX[2] + 6, height - 1, locationLabel(highestTempIndex) + " " + decimalFormat.format(temp));
				}
			}

			screen.refresh();
		}
	}

	private void setup() throws IOException {
		cursorWait(0, 0, 1111);
		typeln(">_ SENSOR SHADOW INTERFACE READY", 0, 0);
		cursorWait(0, 0, 999);
		typeln(">_ INITIALIZING TEMPERATURE SUPPORT TRANSMUTATOR MATRIX", 0, 4);
		cursorWait(0, 0, 888);
		typeln(">_ ....................................................", 0, 6);
		cursorWait(0, 0, 777);
		typeln(">_ CONNECTING TO " + tMS.getTemperatures().length + "-SENSOR SYSTEM", 0, 6);
		cursorWait(0, 0, 666);
	}

	public void cursorWait(int col, int row, int millis) {
		screen.setCursorPosition(null);
		try {
			screen.refresh();
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void typeln(String msg, int col, int row) {
		TextColor defC = foreC;
		foreC = TextColor.ANSI.GREEN;
		int interval = 11;

		for (int i = 0; i < msg.length(); i++) {
			screen.setCursorPosition(new TerminalPosition(col + i, row));
			screen.setCharacter(col + i, row, new TextCharacter(msg.charAt(i), foreC, backC));
			try {
				screen.refresh();
				Thread.sleep(ThreadLocalRandom.current().nextInt(interval * 3));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		foreC = defC;
	}

	public String[] getLocationDescriptions() {
		return locationDescriptions;
	}

	public void setLocationDescriptions(String[] locationDescriptions) {
		this.locationDescriptions = locationDescriptions;
	}
}
