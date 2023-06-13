import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javafx.geometry.Point3D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Iterator;

public class Alpenblumen extends JPanel implements KeyListener, ActionListener, Runnable {

    final GeneralPath cosine_shape;
    final Shape viewport;
    final int width, height, x0, y0, xscale, yscale;
    final AffineTransform view_transform;
    
    final Timer timer;
    long nanotime;
    
    Point3D player;
    double azimuth = 0;
    
    HashSet<Integer> keyspressed = new HashSet<Integer>();

    final static int NUM_MOUNTAINS = 30;
    final static double MAP_RADIUS = 15;
    final static double MIN_CLEARANCE = 2;

    ArrayList<Point3D> mountains = new ArrayList<Point3D>();
    
    HashMap<Point3D,String> mountain_flowers = new HashMap<Point3D,String>();
    HashMap<String,Color> flower_colors = new HashMap<String,Color>();
    HashMap<String,Shape> flower_glyphs = new HashMap<String,Shape>();

    final String flower_types = "34567";
    
    public Alpenblumen() {
	// initialize flower types
	flower_colors.put("3",Color.green);
	flower_colors.put("4",Color.yellow);
	flower_colors.put("5",Color.orange);
	flower_colors.put("6",Color.cyan);
	flower_colors.put("7",Color.magenta);

	for (int i = 0; i < flower_types.length(); i++) {
	    int petals = i+3;
	    GeneralPath path = new GeneralPath();
	    path.moveTo(0,1);
	    for (int j = 0; j < petals; j++) {
		double midangle = 2 * Math.PI * (j + 0.5) / petals;
		double toangle = 2 * Math.PI * (j + 1) / petals;
		path.quadTo(0.5*Math.sin(midangle),0.5*Math.cos(midangle),0,0);
		path.quadTo(0.5*Math.sin(midangle),0.5*Math.cos(midangle),Math.sin(toangle),Math.cos(toangle));
	    }
	    path.closePath();
	    flower_glyphs.put(String.valueOf(flower_types.charAt(i)),path);
	}

	// initialize mountains
	for (int i = 0; i < NUM_MOUNTAINS; i++) {
	    double x, y, z, min, max;
	    boolean acceptable;

	    do {
		x = Math.random()*MAP_RADIUS*2 - MAP_RADIUS;
		y = Math.random()*MAP_RADIUS*2 - MAP_RADIUS;
		min = 0;
		max = MAP_RADIUS;
		
		acceptable = (x*x + y*y <= MAP_RADIUS*MAP_RADIUS);

		Iterator<Point3D> iter = mountains.iterator();
		while (iter.hasNext()) {
		    Point3D other = iter.next();
		    double dx = x - other.getX();
		    double dy = y - other.getY();
		    double distance = Math.sqrt(dx*dx + dy*dy);
		    
		    if (distance < MIN_CLEARANCE) {
			acceptable = false;
		    }

		    if (min < other.getZ() - distance + MIN_CLEARANCE) {
			min = other.getZ() - distance + MIN_CLEARANCE;
		    }
		    if (max > other.getZ() + distance - MIN_CLEARANCE) {
			max = other.getZ() + distance - MIN_CLEARANCE;
		    }
		}
		if (min > max) { acceptable = false; }
	    } while (!acceptable);

	    z = min + Math.random()*(max - min);
	    Point3D new_mountain = new Point3D(x,y,z);
	    mountain_flowers.put(new_mountain, String.valueOf(flower_types.charAt(i % flower_types.length())));
	    mountains.add(new_mountain);
	}

	// initialize player
	player = new Point3D(0,0,0);
	fixHeight();
	
	// initialize width and height
	width = 1280;
	height = 720;

	x0 = width/2;
	y0 = (int)(height * 0.55);

	xscale = (int)(x0 * 0.9);
	yscale = (int)(y0 * 0.9);
	
	viewport = new Rectangle2D.Double(0,0,width,height);

	view_transform = new AffineTransform(xscale,0,0,-yscale,x0,y0);
	
	// initialize cosine shape
	cosine_shape = new GeneralPath();

	cosine_shape.moveTo(-3*Math.PI, -2);
	cosine_shape.lineTo(-3*Math.PI, 1);
	
	double cos_correction = 0.1;
	double s = -1;
	for (int i = -3; i <= 2; i++) {
	    cosine_shape.curveTo(i*Math.PI + Math.PI/3 + cos_correction, s,
				 (i+1)*Math.PI - Math.PI/3 - cos_correction, -s,
				 (i+1)*Math.PI, -s);
	    s = -s;
	}

	cosine_shape.lineTo(3*Math.PI, -2);
	cosine_shape.closePath();
	
	// initialize timer and panel
	timer = new Timer(20, this);

	setPreferredSize(new Dimension(width, height));
	setFocusable(true);
	setFocusTraversalKeysEnabled(false);
	addKeyListener(this);
    }

    void fixHeight() {
	double z = -MAP_RADIUS;
	
	Iterator<Point3D> iter = mountains.iterator();
	while (iter.hasNext()) {
	    Point3D mountain = iter.next();
	    double dx = player.getX() - mountain.getX();
	    double dy = player.getY() - mountain.getY();
	    double distance = Math.sqrt(dx*dx + dy*dy);

	    if (z < mountain.getZ() - distance + 1) {
		z = mountain.getZ() - distance + 1;
	    }
	}
	player = new Point3D(player.getX(),player.getY(),z);
    }
    
    public void start() {
	nanotime = System.nanoTime();
	timer.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Alpenblumen());
    }

    // implements Runnable
    public void run() {
	JFrame f = new JFrame("alpenblumen");
	f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	f.add(this);
	f.pack();
	f.setResizable(false);
	f.setVisible(true);

	start();
    }
    
    // implements ActionListener                                                                                   

    public void actionPerformed(ActionEvent e) {
        long newnanotime = System.nanoTime();
        long delta = newnanotime - nanotime;
        double dt = delta * 0.000000001;
        nanotime = newnanotime;

        update(dt);

        repaint();
    }

    // implements KeyListener                                                                                      
    public void keyTyped (KeyEvent e) { }

    public void keyPressed (KeyEvent e) {
        markKey(e.getKeyCode());
    }

    public void keyReleased (KeyEvent e) {
        clearKey(e.getKeyCode());
    }

    public void markKey (int keycode) {
        keyspressed.add(new Integer(keycode));
    }

    public void clearKey (int keycode) {
        keyspressed.remove(new Integer(keycode));
    }

    public boolean checkKey (int keycode) {
        return keyspressed.contains(new Integer(keycode));
    }

    // update
    public void update(double dt) {
	if (checkKey(KeyEvent.VK_RIGHT)) {
	    azimuth += dt;
	    if (azimuth > Math.PI) {
		azimuth -= 2*Math.PI;
	    }
	}
	if (checkKey(KeyEvent.VK_LEFT)) {
	    azimuth -= dt;
	    if (azimuth < -Math.PI) {
		azimuth += 2*Math.PI;
	    }
	}
	if (checkKey(KeyEvent.VK_UP)) {
	    player = new Point3D(player.getX() + dt*Math.sin(azimuth),
				 player.getY() + dt*Math.cos(azimuth),
				 player.getZ());
	    fixHeight();
	}
	if (checkKey(KeyEvent.VK_DOWN)) {
	    player = new Point3D(player.getX() - dt*Math.sin(azimuth),
				 player.getY() - dt*Math.cos(azimuth),
				 player.getZ());
	    fixHeight();
	}
    }
    
    // draw graphics
    public void paintComponent(Graphics g) {
        Graphics2D graphics = (Graphics2D)g;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

	graphics.setColor(Color.black);
	graphics.fill(viewport);
	
	// draw the mountains
	mountains.sort(Comparator.comparing(p -> -(
						  (p.getX() - player.getX())*(p.getX() - player.getX()) +
						  (p.getY() - player.getY())*(p.getY() - player.getY())
						   )));

	mountains.forEach(p -> {
		double dx, dy, dz, distance, m_azimuth, elevation;
		dx = p.getX() - player.getX();
		dy = p.getY() - player.getY();		
		dz = p.getZ() - player.getZ();		

		distance = Math.sqrt(dx*dx+dy*dy);
		if (distance > 0) {
		    elevation = dz / distance;
		    if (elevation < -1) {
			elevation = -1;
		    }
		    m_azimuth = Math.atan2(dx,dy);
		}
		else {
		    elevation = -1;
		    m_azimuth = azimuth;
		}
		
		float fog = (float)Math.exp(-distance*0.8/MAP_RADIUS);
		graphics.setColor(new Color(fog,fog,fog));
		double cone_angle = Math.acos(elevation);

		Area mountain = new Area(AffineTransform.getTranslateInstance(cone_angle,0).createTransformedShape(cosine_shape));
		mountain.intersect(new Area(AffineTransform.getTranslateInstance(-cone_angle,0).createTransformedShape(cosine_shape)));

		GeneralPath mask = new GeneralPath();
		mask.moveTo(-2*Math.PI,-2);
		mask.lineTo(-2*Math.PI,1);
		for (int i = -1; i <= 1; i += 2) {
		    for (int j = -1; j <= 1; j += 2) {
			for (int k = -1; k <= 1; k += 2) {
			    mask.lineTo(i*Math.PI + j*cone_angle, j*k);
			}
		    }
		}
		mask.lineTo(2*Math.PI,1);
		mask.lineTo(2*Math.PI,-2);
		mask.closePath();

		mountain.intersect(new Area(mask));

		m_azimuth -= azimuth;
		if (m_azimuth < -Math.PI) { m_azimuth += 2*Math.PI; }
		if (m_azimuth > Math.PI) { m_azimuth -= 2*Math.PI; }

		AffineTransform mountain_view = (AffineTransform)view_transform.clone();
		mountain_view.concatenate(AffineTransform.getTranslateInstance(m_azimuth,0));
		graphics.fill(mountain_view.createTransformedShape(mountain));

		// draw flower
		if (distance < 0.75) {
		    distance = 0.75;
		}
		String flower = mountain_flowers.get(p);
		graphics.setColor(flower_colors.get(flower));
	        mountain_view.concatenate(AffineTransform.getTranslateInstance(0,elevation));
		mountain_view.concatenate(AffineTransform.getScaleInstance(0.5*yscale/(xscale*distance),0.5/distance));
		mountain_view.concatenate(AffineTransform.getTranslateInstance(0,1.25));
		graphics.fill(mountain_view.createTransformedShape(flower_glyphs.get(flower)));
	    });

    }
}
