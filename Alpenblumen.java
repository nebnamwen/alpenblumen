import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.Iterator;

public class Alpenblumen extends JPanel implements KeyListener, ActionListener, Runnable {

    class Point3D {
	public final double x,y,z;

	public Point3D(double x, double y, double z) {
	    this.x = x;
	    this.y = y;
	    this.z = z;
	}
    }

    class FlowerType {
	public final Color color;
	public final Shape shape;

	public FlowerType(int petals, Color c) {
	    this.color = c;

	    GeneralPath path = new GeneralPath();
	    path.moveTo(0,1);
	    for (int j = 0; j < petals; j++) {
		double midangle = 2 * Math.PI * (j + 0.5) / petals;
		double toangle = 2 * Math.PI * (j + 1) / petals;
		path.quadTo(0.7*Math.sin(midangle),0.7*Math.cos(midangle),0,0);
		path.quadTo(0.7*Math.sin(midangle),0.7*Math.cos(midangle),Math.sin(toangle),Math.cos(toangle));
	    }
	    path.closePath();
	    this.shape = path;
	}
    }

    class Mountain {
	public final Point3D pos;
	public FlowerType flower;

	public Mountain(Point3D pos, FlowerType flower) {
	    this.pos = pos;
	    this.flower = flower;
	}
    }
    
    final GeneralPath cosine_shape, score_shape, inventory_shape;
    final Shape viewport;
    final int width, height, x0, y0, xscale, yscale;
    final AffineTransform view_transform;
    
    final Timer timer;
    long nanotime;
    
    Point3D player;
    double azimuth = 0;
    FlowerType[] inventory = new FlowerType[2];
    int score = 0;
    
    HashSet<Integer> keyspressed = new HashSet<Integer>();

    final static int NUM_MOUNTAINS = 30;
    final static double MAP_RADIUS = 15;
    final static double MIN_CLEARANCE = 2;

    ArrayList<FlowerType> flower_types = new ArrayList<FlowerType>();
    ArrayList<Mountain> mountains = new ArrayList<Mountain>();
    
    public Alpenblumen() {
	// initialize flower types
	flower_types.add(new FlowerType(3,Color.green));
	flower_types.add(new FlowerType(4,Color.yellow));
	flower_types.add(new FlowerType(5,Color.orange));
	flower_types.add(new FlowerType(6,Color.cyan));
	flower_types.add(new FlowerType(7,Color.magenta));

	// initialize cosine shape that will be used to draw mountains
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

	// initialize HUD shapes
	// star for score display
	score_shape = new GeneralPath();
	score_shape.moveTo(0,1);
	for (int i = 0; i < 5; i++) {
		double midangle = 2 * Math.PI * (i + 0.5) / 5;
		double toangle = 2 * Math.PI * (i + 1) / 5;
		score_shape.lineTo(0.5*Math.sin(midangle),0.5*Math.cos(midangle));
		score_shape.lineTo(Math.sin(toangle),Math.cos(toangle));
	    }
	score_shape.closePath();
	
	// rounded square for inventory display
	inventory_shape = new GeneralPath();
	inventory_shape.moveTo(0,1);
	inventory_shape.quadTo(1,1,1,0);
	inventory_shape.quadTo(1,-1,0,-1);
	inventory_shape.quadTo(-1,-1,-1,0);
	inventory_shape.quadTo(-1,1,0,1);
	inventory_shape.closePath();
	
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

		Iterator<Mountain> iter = mountains.iterator();
		while (iter.hasNext()) {
		    Point3D other = iter.next().pos;
		    double dx = x - other.x;
		    double dy = y - other.y;
		    double distance = Math.sqrt(dx*dx + dy*dy);
		    
		    if (distance < MIN_CLEARANCE) {
			acceptable = false;
		    }

		    if (min < other.z - distance + MIN_CLEARANCE) {
			min = other.z - distance + MIN_CLEARANCE;
		    }
		    if (max > other.z + distance - MIN_CLEARANCE) {
			max = other.z + distance - MIN_CLEARANCE;
		    }
		}
		if (min > max) { acceptable = false; }
	    } while (!acceptable);

	    z = min + Math.random()*(max - min);
	    Point3D pos = new Point3D(x,y,z);
	    mountains.add(new Mountain(pos, flower_types.get(i % flower_types.size())));
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
	
	// initialize timer and panel
	timer = new Timer(20, this);

	setPreferredSize(new Dimension(width, height));
	setFocusable(true);
	setFocusTraversalKeysEnabled(false);
	addKeyListener(this);
    }

    void fixHeight() {
	// set the player's z coordinate to be just above the surface
	// formed by the union of all the conical mountains
	double z = -MAP_RADIUS;
	
	Iterator<Mountain> iter = mountains.iterator();
	while (iter.hasNext()) {
	    Point3D mountain = iter.next().pos;
	    double dx = player.x - mountain.x;
	    double dy = player.y - mountain.y;
	    double distance = Math.sqrt(dx*dx + dy*dy);

	    if (z < mountain.z - distance + 1) {
		z = mountain.z - distance + 1;
	    }
	}
	player = new Point3D(player.x,player.y,z);
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

    public boolean isFlowerPickable(Mountain m) {
	if (m.flower == null) {
	    // the flower on this mountain was already picked
	    return false;
	}
	if (inventory[0] != null && !inventory[0].equals(m.flower) && inventory[1] != null && !inventory[1].equals(m.flower)) {
	    // we neither have an empty inventory slot to hold this flower
	    // nor a matching flower in inventory to pair it with
	    return false;
	}

	double dx, dy, dz, distance, m_azimuth;
	dx = m.pos.x - player.x;
	dy = m.pos.y - player.y;		
	dz = m.pos.z - player.z;		

	distance = Math.sqrt(dx*dx+dy*dy);
	if (distance > 0) {
	    m_azimuth = Math.atan2(dx,dy);
	}
	else {
	    m_azimuth = azimuth;
	}
	
	if (distance <= 0.85 && Math.cos(m_azimuth - azimuth) >= 0.75) {
	    // this flower is close enough to reach and we are facing towards it
	    return true;
	}
	else {
	    // flower is out of range
	    return false;
	}
    }
    
    public Mountain pickableFlower() {
	Iterator<Mountain> iter = mountains.iterator();
        while (iter.hasNext()) {
            Mountain m = iter.next();
	    if (isFlowerPickable(m)) {
		return m;
	    }
	}
	return null;
    }
    
    // update
    public void update(double dt) {
	// space bar is pressed -- try to pick a flower if one is in reach
	if (checkKey(KeyEvent.VK_SPACE)) {
	    Mountain m = pickableFlower();
	    if (m != null) {
		boolean picked = false;
		for (int i = 0; i <= 1; i++) {
		    if (!picked && m.flower.equals(inventory[i])) {
			// flower on this mountain matches one in inventory
			// clear both and score a point
			inventory[i] = null;
			m.flower = null;
			score += 1;
			picked = true;
		    }
		}
		for (int i = 0; i <= 1; i++) {
		    if (!picked && inventory[i] == null) {
			// open slot in inventory
			// pick this flower and store it
			inventory[i] = m.flower;
			m.flower = null;
			picked = true;
		    }
		}
	    }
	    clearKey(KeyEvent.VK_SPACE);
	}
	
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
	    player = new Point3D(player.x + 1.25*dt*Math.sin(azimuth),
				 player.y + 1.25*dt*Math.cos(azimuth),
				 player.z);
	    fixHeight();
	}
	if (checkKey(KeyEvent.VK_DOWN)) {
	    player = new Point3D(player.x - dt*Math.sin(azimuth),
				 player.y - dt*Math.cos(azimuth),
				 player.z);
	    fixHeight();
	}
    }
    
    // draw graphics
    public void paintComponent(Graphics g) {
        Graphics2D graphics = (Graphics2D)g;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

	graphics.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
	
	graphics.setColor(Color.black);
	graphics.fill(viewport);
	
	// draw the mountains
	mountains.sort(Comparator.comparing(m -> -(
						   (m.pos.x - player.x)*(m.pos.x - player.x) +
						   (m.pos.y - player.y)*(m.pos.y - player.y)
						   )));

	mountains.forEach(m -> {
		// each mountain is drawn as the masked area intersection of two cosine curves
		// and shaded according to its distance from the player
		
		double dx, dy, dz, distance, m_azimuth, elevation;
		Point3D p = m.pos;
		
		dx = p.x - player.x;
		dy = p.y - player.y;		
		dz = p.z - player.z;		

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
		if (m.flower != null) {
		    if (distance < 0.75) {
			distance = 0.75;
		    }
		    graphics.setColor(m.flower.color);
		    mountain_view.concatenate(AffineTransform.getTranslateInstance(0,elevation));
		    mountain_view.concatenate(AffineTransform.getScaleInstance(0.5*yscale/(xscale*distance),0.5/distance));
		    mountain_view.concatenate(AffineTransform.getTranslateInstance(0,1.25));
		    Shape transformed_flower = mountain_view.createTransformedShape(m.flower.shape);
		    graphics.fill(transformed_flower);

		    if (isFlowerPickable(m)) {
			graphics.setColor(Color.white);
			graphics.draw(transformed_flower);
		    }
		}
	    });

	// draw score
	AffineTransform score_transform = AffineTransform.getScaleInstance(yscale * 0.05, -yscale * 0.05);
	score_transform.translate(1.5, -1.5);
	for (int i = 0; i < NUM_MOUNTAINS / 2; i++) {
	    Shape this_score_shape = score_transform.createTransformedShape(score_shape);
	    if (score > i) {
		graphics.setColor(Color.white);
		if (score == NUM_MOUNTAINS / 2) {
		    graphics.setColor(flower_types.get(i % flower_types.size()).color);
		}
		graphics.fill(this_score_shape);
	    }
	    graphics.setColor(Color.white);
	    graphics.draw(this_score_shape);

	    score_transform.translate(3, 0);
	}
	
	// draw inventory
	AffineTransform inventory_transform = AffineTransform.getScaleInstance(yscale * 0.1, -yscale * 0.1);
	inventory_transform.translate(22.0 * xscale/yscale - 4, -1.25);
	for (int i = 0; i <= 1; i++) {
	    graphics.setColor(Color.gray);
	    Shape this_inventory_shape = inventory_transform.createTransformedShape(inventory_shape);
	    graphics.draw(this_inventory_shape);
	    graphics.fill(this_inventory_shape);

	    if (inventory[i] != null) {
		graphics.setColor(inventory[i].color);
		graphics.fill(inventory_transform.createTransformedShape(inventory[i].shape));
	    }
	    inventory_transform.translate(2.5, 0);
	}
    }
    
    public void start() {
	nanotime = System.nanoTime();
	timer.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Alpenblumen());
    }
}
