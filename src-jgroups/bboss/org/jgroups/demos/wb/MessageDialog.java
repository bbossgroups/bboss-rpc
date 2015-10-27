// $Id: MessageDialog.java,v 1.2 2004/09/23 16:29:34 belaban Exp $

package bboss.org.jgroups.demos.wb;


import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Point;
import java.awt.TextArea;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;



public class MessageDialog extends Dialog implements ActionListener {
    private final TextArea    text=new TextArea("");
    private final Font  default_font=new Font("Helvetica",Font.PLAIN,12);
    

    public MessageDialog(Frame parent, String sender, String msg) {
	super(parent, "Msg from " + sender);

	Button ok=new Button("OK");

	setLayout(new BorderLayout());
	setBackground(Color.white);

	ok.setFont(default_font);
	text.setFont(default_font);
	text.setEditable(false);
	text.setText(msg);

	ok.addActionListener(this);
	
	add("Center", text);
	add("South", ok);
	
	setSize(300, 150);

	Point my_loc=parent.getLocation();
	my_loc.x+=50;
	my_loc.y+=150;
	setLocation(my_loc);
	Toolkit.getDefaultToolkit().beep();
	show();
    }


    public void actionPerformed(ActionEvent e) {
	dispose();
    }


}
