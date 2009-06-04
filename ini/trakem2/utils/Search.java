/**

TrakEM2 plugin for ImageJ(C).
Copyright (C) 2005-2009 Albert Cardona and Rodney Douglas.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation (http://www.gnu.org/licenses/gpl.txt )

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA. 

You may contact Albert Cardona at acardona at ini.phys.ethz.ch
Institute of Neuroinformatics, University of Zurich / ETH, Switzerland.
**/

package ini.trakem2.utils;

import ini.trakem2.Project;
import ini.trakem2.persistence.DBObject;
import ini.trakem2.display.*;
import ini.trakem2.ControlWindow;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.event.*;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import java.util.regex.*;

public class Search {
	private JFrame search_frame = null;
	private JTabbedPane search_tabs = null;
	private JTextField search_field = null;
	private JComboBox pulldown = null;
	private KeyListener kl = null;

	static private Search instance = null;

	private Class[] types = null;


	/** Creates the GUI for searching text in any TrakEM2 element. */
	public Search() {
		if (null != instance) {
			instance.makeGUI();
		} else {
			instance = this;
			types =  new Class[]{DBObject.class, Displayable.class, DLabel.class, Patch.class, AreaList.class, Profile.class, Pipe.class, Ball.class, Layer.class, Dissector.class};
			makeGUI();
		}
	}

	private void tryCloseTab(KeyEvent ke) {
		switch (ke.getKeyCode()) {
			case KeyEvent.VK_W:
				if (!ke.isControlDown()) return;
				int ntabs = search_tabs.getTabCount();
				if (0 == ntabs) {
					instance.destroy();
					return;
				}
				search_tabs.remove(search_tabs.getSelectedIndex());
				return;
			default:
				return;
		}
	}

	private void makeGUI() {
		// create GUI if not there
		if (null == search_frame) {
			search_frame = ControlWindow.createJFrame("Search");
			search_frame.addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent we) {
					instance.destroy();
				}
			});
			search_tabs = new JTabbedPane(JTabbedPane.TOP);
			kl = new KeyAdapter() {
				public void keyPressed(KeyEvent ke) {
					tryCloseTab(ke);
				}
			};
			search_tabs.addKeyListener(kl);
			search_field = new JTextField(14);
			search_field.addKeyListener(new VKEnterListener());
			JButton b = new JButton("Search");
			b.addActionListener(new ButtonListener());
			pulldown = new JComboBox(new String[]{"All", "All displayables", "Labels", "Images", "Area Lists", "Profiles", "Pipes", "Balls", "Layers", "Dissectors"});
			JPanel top = new JPanel();
			top.add(search_field);
			top.add(b);
			top.add(pulldown);
			top.setMinimumSize(new Dimension(400, 30));
			top.setMaximumSize(new Dimension(10000, 30));
			top.setPreferredSize(new Dimension(400, 30));
			JPanel all = new JPanel();
			all.setPreferredSize(new Dimension(400, 400));
			BoxLayout bl = new BoxLayout(all, BoxLayout.Y_AXIS);
			all.setLayout(bl);
			all.add(top);
			all.add(search_tabs);
			search_frame.getContentPane().add(all);
			search_frame.pack();
			search_frame.setVisible(true);
		} else {
			search_frame.toFront();
		}
	}

	synchronized private void destroy() {
		if (null != instance) {
			if (null != search_frame) search_frame.dispose();
			search_frame = null;
			search_tabs = null;
			search_field = null;
			pulldown = null;
			types = null;
			kl = null;
			// deregister
			instance = null;
		}
	}

	private class DisplayableTableModel extends AbstractTableModel {
		private Vector v_obs;
		private Vector v_txt;
		DisplayableTableModel(Vector v_obs, Vector v_txt) {
			super();
			this.v_obs = v_obs;
			this.v_txt = v_txt;
		}
		public String getColumnName(int col) {
			if (0 == col) return "Type";
			else if (1 == col) return "Info";
			else if (2 == col) return "Matched";
			else return "";
		}
		public int getRowCount() { return v_obs.size(); }
		public int getColumnCount() { return 3; }
		public Object getValueAt(int row, int col) {
			if (0 == col) return Project.getName(v_obs.get(row).getClass());
			else if (1 == col) return ((DBObject)v_obs.get(row)).getShortTitle();
			else if (2 == col) return v_txt.get(row).toString();
			else return "";
		}
		public DBObject getDBObjectAt(int row) {
			return (DBObject)v_obs.get(row);
		}
		public Displayable getDisplayableAt(int row) {
			return (Displayable)v_obs.get(row);
		}
		public boolean isCellEditable(int row, int col) {
			return false;
		}
		public void setValueAt(Object value, int row, int col) {
			// nothing
			//fireTableCellUpdated(row, col);
		}
		public boolean remove(Displayable displ) {
			int i = v_obs.indexOf(displ);
			if (-1 != i) {
				v_obs.remove(i);
				v_txt.remove(i);
				return true;
			}
			return false;
		}
		public boolean contains(Object ob) {
			return v_obs.contains(ob);
		}
	}

	private void executeSearch() {
		String pattern = search_field.getText();
		if (null == pattern || 0 == pattern.length()) {
			return;
		}
		// fix pattern
		final String typed_pattern = pattern;
		final StringBuffer sb = new StringBuffer(); // I hate java
		if (!pattern.startsWith("^")) sb.append("^.*");
		for (int i=0; i<pattern.length(); i++) {
			sb.append(pattern.charAt(i));
		}
		if (!pattern.endsWith("$")) sb.append(".*$");
		pattern = sb.toString();
		final Pattern pat = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
		//Utils.log2("pattern after: " + pattern);
		final ArrayList al = new ArrayList();
		//Utils.log("types[pulldown] = " + types[pulldown.getSelectedIndex()]);
		find(ControlWindow.getActive().getRootLayerSet(), al, types[pulldown.getSelectedIndex()]);
		//Utils.log2("found labels: " + al.size());
		if (0 == al.size()) return;
		final Vector v_obs = new Vector();
		final Vector v_txt = new Vector();
		for (Iterator it = al.iterator(); it.hasNext(); ) {
			final DBObject dbo = (DBObject)it.next();
			boolean matched = false;
			// Search in its title
			String txt =  dbo instanceof Displayable ?
				  dbo.getProject().getMeaningfulTitle((Displayable)dbo)
				: dbo.getTitle();
			matched = pat.matcher(txt).matches();
			long id = dbo.getId();
			if (87 == id) Utils.log2("Searching title: " + txt + (matched ? "********** TRUE" : ""));
			if (!matched) {
				// Search also in its toString()
				txt = dbo.toString();
				matched = pat.matcher(txt).matches();
				if (87 == id) Utils.log2("Searching toString: " + txt + (matched ? "********** TRUE" : ""));
			}
			if (!matched) {
				// Search also in its id
				txt = Long.toString(dbo.getId());
				matched = pat.matcher(txt).matches();
				if (matched) txt = "id: #" + txt;
				if (87 == id) Utils.log2("Searching ID: " + txt + (matched ? "********** TRUE" : ""));
			}
			if (!matched) continue;

			//txt = txt.length() > 30 ? txt.substring(0, 27) + "..." : txt;
			v_obs.add(dbo);
			v_txt.add(txt);
		}

		if (0 == v_obs.size()) {
			Utils.showMessage("Nothing found.");
			return;
		}
		final JScrollPane jsp = makeTable(new DisplayableTableModel(v_obs, v_txt));
		search_tabs.addTab(typed_pattern, jsp);
		search_tabs.setSelectedComponent(jsp);
		search_frame.pack();
	}

	private JScrollPane makeTable(TableModel model) {
		JTable table = new JTable(model);
		//java 1.6.0 only!! //table.setAutoCreateRowSorter(true);
		table.addMouseListener(new DisplayableListListener());
		table.addKeyListener(kl);
		JScrollPane jsp = new JScrollPane(table);
		jsp.setPreferredSize(new Dimension(500, 500));
		return jsp;
	}

	/** Listen to the search button. */
	private class ButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent ae) {
			executeSearch();
		}
	}
	/** Listen to the search field. */
	private class VKEnterListener extends KeyAdapter {
		public void keyPressed(KeyEvent ke) {
			if (ke.getKeyCode() == KeyEvent.VK_ENTER) {
				executeSearch();
				return;
			}
			tryCloseTab(ke);
		}
	}

	/** Listen to double clicks in the table rows. */
	private class DisplayableListListener extends MouseAdapter {
		public void mousePressed(MouseEvent me) {
			final JTable table = (JTable)me.getSource();
			final DBObject ob = ((DisplayableTableModel)table.getModel()).getDBObjectAt(table.rowAtPoint(me.getPoint()));
			if (2 == me.getClickCount()) {
				// is a table//Utils.log2("LLL source is " + me.getSource());
				// JTable is AN ABSOLUTE PAIN to work with
				// ???? THERE IS NO OBVIOUS WAY to retrieve the data. How lame.
				// Ah, a "model" ... since when a model holds the DATA (!!), same with JTree, how lame.
				if (ob instanceof Displayable) {
					Displayable displ = (Displayable)ob;
					Display.showCentered(displ.getLayer(), displ, true, me.isShiftDown());
				} else if (ob instanceof Layer) {
					Display.showFront((Layer)ob);
				} else {
					Utils.log2("Search: Unhandable table selection: " + ob);
				}
			} else if (Utils.isPopupTrigger(me)) {
				JPopupMenu popup = new JPopupMenu();
				final String show2D = "Show";
				final String select = "Select in display";
				ActionListener listener = new ActionListener() {
					public void actionPerformed(ActionEvent ae) {
						final String command = ae.getActionCommand();
						if (command.equals(show2D)) {
							if (ob instanceof Displayable) {
								Displayable displ = (Displayable)ob;
								Display.showCentered(displ.getLayer(), displ, true, 0 != (ae.getModifiers() & ActionEvent.SHIFT_MASK));
							} else if (ob instanceof Layer) {
								Display.showFront((Layer)ob);
							}
						} else if (command.equals(select)) {
							if (ob instanceof Layer) {
								Display.showFront((Layer)ob);
							} else if (ob instanceof Displayable) {
								Displayable displ = (Displayable)ob;
								if (!displ.isVisible()) displ.setVisible(true);
								Display display = Display.getFront(displ.getProject());
								if (null == display) return;
								boolean shift_down = 0 != (ae.getModifiers() & ActionEvent.SHIFT_MASK);
								display.select(displ, shift_down);
							}
						}
					}
				};
				JMenuItem item = new JMenuItem(show2D); popup.add(item); item.addActionListener(listener);
				item = new JMenuItem(select); popup.add(item); item.addActionListener(listener);
				popup.show(table, me.getX(), me.getY());
			}
		}
	}

	/** Recursive search into nested LayerSet instances, accumulating instances of type into the list al. */
	private void find(final LayerSet set, final ArrayList al, final Class type) {
		if (type == DBObject.class) {
			al.add(set);
		}
		for (ZDisplayable zd : set.getZDisplayables()) {
			if (DBObject.class == type || Displayable.class == type) {
				al.add(zd);
			} else if (zd.getClass() == type) {
				al.add(zd);
			}
		}
		for (Layer layer : set.getLayers()) {
			if (DBObject.class == type || Layer.class == type) {
				al.add(layer);
			}
			for (Displayable ob : layer.getDisplayables()) {
				if (DBObject.class == type || Displayable.class == type) {
					if (ob instanceof LayerSet) find((LayerSet)ob, al, type);
					else al.add(ob);
				} else if (ob.getClass() == type) {
					al.add(ob);
				}
			}
		}
	}

	/** Remove from the tables if there. */
	static public void remove(final Displayable displ) {
		if (null == displ || null == instance) return;
		final int n_tabs = instance.search_tabs.getTabCount();
		boolean repaint = false;
		final int selected = instance.search_tabs.getSelectedIndex();
		for (int t=0; t<n_tabs; t++) {
			java.awt.Component c = instance.search_tabs.getComponentAt(t);
			JTable table = (JTable)((JScrollPane)c).getViewport().getComponent(0);
			DisplayableTableModel data = (DisplayableTableModel)table.getModel();
			if (data.remove(displ)) {
				// remake table (can't delete just a row, only columns??)
				String name = instance.search_tabs.getTitleAt(t);
				instance.search_tabs.removeTabAt(t);
				// need to think about it TODO // if (0 == data.getRowCount()) continue;
				instance.search_tabs.insertTab(name, null, instance.makeTable(data), "", t);
				if (t == selected) repaint = true;
				try { Thread.sleep(100); } catch (Exception e) {} // I love swing
			}
		}
		if (repaint) {
			Utils.updateComponent(instance.search_frame);
			instance.search_tabs.setSelectedIndex(selected);
		}
	}

	/** Repaint (refresh the text in the cells) only if any of the selected tabs in any of the search frames contains the given object in its rows. */
	static public void repaint(final Object ob) {
		if (null == instance) return;
		final int selected = instance.search_tabs.getSelectedIndex();
		if (-1 == selected) return;
		java.awt.Component c = instance.search_tabs.getComponentAt(selected);
		JTable table = (JTable)((JScrollPane)c).getViewport().getComponent(0);
		DisplayableTableModel data = (DisplayableTableModel)table.getModel();
		if (data.contains(ob)) {
			Utils.updateComponent(instance.search_frame);
		}
	}
}
