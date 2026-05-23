import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/**
 * PhysicsDemo — Swing application with two live physics simulations:
 *   Tab 1: Projectile Motion (Euler integration, 2D kinematics)
 *   Tab 2: Three-Body Gravitational System (RK4 integration)
 *
 * Design palette: dark navy background (#1a1a2e), bright accents, white HUD text.
 */
public class PhysicsDemo extends JFrame {

    // -----------------------------------------------------------------------
    // Shared color palette
    // -----------------------------------------------------------------------
    static final Color BG_COLOR       = new Color(0x1a, 0x1a, 0x2e);
    static final Color GRID_COLOR     = new Color(0x2a, 0x2a, 0x4a);
    static final Color AXIS_COLOR     = new Color(0x55, 0x55, 0x88);
    static final Color HUD_COLOR      = Color.WHITE;
    static final Color ACCENT_YELLOW  = new Color(0xFF, 0xD7, 0x00);
    static final Color ACCENT_CYAN    = new Color(0x00, 0xE5, 0xFF);
    static final Color ACCENT_ORANGE  = new Color(0xFF, 0x6B, 0x35);
    static final Color PANEL_BG       = new Color(0x10, 0x10, 0x22);
    static final Color CONTROL_BG     = new Color(0x16, 0x16, 0x2e);
    static final Font  HUD_FONT       = new Font("Monospaced", Font.PLAIN, 12);
    static final Font  HUD_BOLD       = new Font("Monospaced", Font.BOLD,  13);
    static final Font  LABEL_FONT     = new Font("SansSerif",  Font.PLAIN, 11);

    public PhysicsDemo() {
        super("Physics Demo — Projectile Motion & Three-Body Gravity");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 780);
        setMinimumSize(new Dimension(960, 640));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_COLOR);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG_COLOR);
        tabs.setForeground(Color.WHITE);
        tabs.setFont(new Font("SansSerif", Font.BOLD, 13));

        ProjectilePanel projectilePanel = new ProjectilePanel();
        ThreeBodyPanel  threeBodyPanel  = new ThreeBodyPanel();

        tabs.addTab("  Projectile Motion  ", projectilePanel);
        tabs.addTab("  Three-Body Gravity  ", threeBodyPanel);

        // Stop inactive tab's timer to save resources
        tabs.addChangeListener(e -> {
            int sel = tabs.getSelectedIndex();
            if (sel == 0) {
                threeBodyPanel.pauseTimer();
            } else {
                // projectile only runs while animating, no forced pause needed
            }
        });

        add(tabs);
        setVisible(true);
    }

    // =======================================================================
    // Utility: style a JButton with dark theme
    // =======================================================================
    static JButton makeButton(String text, Color fg) {
        JButton btn = new JButton(text);
        btn.setBackground(new Color(0x22, 0x22, 0x44));
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(fg.darker(), 1),
            BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // =======================================================================
    // Utility: style a JSlider with dark theme
    // =======================================================================
    static JSlider makeSlider(int min, int max, int value) {
        JSlider s = new JSlider(min, max, value);
        s.setBackground(CONTROL_BG);
        s.setForeground(Color.LIGHT_GRAY);
        s.setPaintTicks(true);
        s.setPaintLabels(false);
        s.setMinorTickSpacing((max - min) / 10);
        s.setMajorTickSpacing((max - min) / 5);
        return s;
    }

    // =======================================================================
    // Utility: create a TitledBorder with white text on dark background
    // =======================================================================
    static TitledBorder makeTitledBorder(String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(AXIS_COLOR, 1), title);
        tb.setTitleColor(ACCENT_CYAN);
        tb.setTitleFont(new Font("SansSerif", Font.BOLD, 11));
        return tb;
    }

    // =======================================================================
    //  TAB 1 — PROJECTILE MOTION
    // =======================================================================
    static class ProjectilePanel extends JPanel {

        // --- Physics constants ---
        static final double PIXELS_PER_METER = 2.0;   // 1 m = 2 px  (fits ~500 m range)
        static final double DT               = 0.016;  // seconds per frame (~62.5 fps physics)
        static final int    CANVAS_W         = 1000;
        static final int    CANVAS_H         = 600;

        // --- State ---
        double posX, posY;          // meters from launch origin
        double velX, velY;          // m/s
        double time;                // elapsed seconds
        double maxHeight;           // metres, peak recorded so far
        double gravity;             // m/s² (down), read from slider
        boolean launched  = false;
        boolean landed    = false;

        // Trail: list of (x,y) in meters
        final List<double[]> trail = new ArrayList<>(4000);

        // Camera: world-space center (meters) that maps to the screen center
        double camX = 0, camY = 0;

        // --- Controls ---
        JSlider   sliderAngle;
        JSlider   sliderSpeed;
        JSlider   sliderGravity;
        JLabel    lblAngle, lblSpeed, lblGravity;
        JButton   btnLaunch, btnReset;
        JCheckBox chkAirResist;
        JSlider   sliderDrag;
        JLabel    lblDrag;
        double    dragCoef = 0.0;

        // --- Canvas ---
        ProjectileCanvas canvas;
        javax.swing.Timer animTimer;

        ProjectilePanel() {
            setLayout(new BorderLayout(4, 4));
            setBackground(BG_COLOR);

            canvas = new ProjectileCanvas();
            canvas.setBorder(BorderFactory.createLineBorder(AXIS_COLOR, 1));
            add(canvas, BorderLayout.CENTER);
            add(buildControls(), BorderLayout.EAST);

            // Timer: ~62 fps
            animTimer = new javax.swing.Timer(16, e -> step());
            animTimer.setCoalesce(true);

            resetState();
        }

        // ----------------------------------------------------------------
        JPanel buildControls() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(CONTROL_BG);
            panel.setBorder(new EmptyBorder(12, 12, 12, 12));
            panel.setPreferredSize(new Dimension(220, 0));

            // Title
            JLabel title = new JLabel("Controls");
            title.setForeground(ACCENT_CYAN);
            title.setFont(new Font("SansSerif", Font.BOLD, 15));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(title);
            panel.add(Box.createVerticalStrut(14));

            // --- Angle ---
            JPanel anglePanel = new JPanel(new BorderLayout());
            anglePanel.setBackground(CONTROL_BG);
            anglePanel.setBorder(makeTitledBorder("Launch Angle"));
            sliderAngle = makeSlider(0, 90, 45);
            lblAngle    = new JLabel("45°");
            lblAngle.setForeground(ACCENT_YELLOW);
            lblAngle.setFont(HUD_BOLD);
            lblAngle.setHorizontalAlignment(SwingConstants.RIGHT);
            sliderAngle.addChangeListener(e -> {
                lblAngle.setText(sliderAngle.getValue() + "°");
                if (!launched) resetState();
            });
            anglePanel.add(sliderAngle, BorderLayout.CENTER);
            anglePanel.add(lblAngle,    BorderLayout.EAST);
            anglePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(anglePanel);
            panel.add(Box.createVerticalStrut(8));

            // --- Speed ---
            JPanel speedPanel = new JPanel(new BorderLayout());
            speedPanel.setBackground(CONTROL_BG);
            speedPanel.setBorder(makeTitledBorder("Initial Speed (m/s)"));
            sliderSpeed = makeSlider(10, 100, 50);
            lblSpeed    = new JLabel("50 m/s");
            lblSpeed.setForeground(ACCENT_YELLOW);
            lblSpeed.setFont(HUD_BOLD);
            lblSpeed.setHorizontalAlignment(SwingConstants.RIGHT);
            sliderSpeed.addChangeListener(e -> {
                lblSpeed.setText(sliderSpeed.getValue() + " m/s");
                if (!launched) resetState();
            });
            speedPanel.add(sliderSpeed, BorderLayout.CENTER);
            speedPanel.add(lblSpeed,    BorderLayout.EAST);
            speedPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(speedPanel);
            panel.add(Box.createVerticalStrut(8));

            // --- Gravity ---
            JPanel gravPanel = new JPanel(new BorderLayout());
            gravPanel.setBackground(CONTROL_BG);
            gravPanel.setBorder(makeTitledBorder("Gravity (m/s²)"));
            sliderGravity = makeSlider(1, 20, 10);
            lblGravity    = new JLabel("10 m/s²");
            lblGravity.setForeground(ACCENT_YELLOW);
            lblGravity.setFont(HUD_BOLD);
            lblGravity.setHorizontalAlignment(SwingConstants.RIGHT);
            sliderGravity.addChangeListener(e -> {
                lblGravity.setText(sliderGravity.getValue() + " m/s²");
                if (!launched) resetState();
            });
            gravPanel.add(sliderGravity, BorderLayout.CENTER);
            gravPanel.add(lblGravity,    BorderLayout.EAST);
            gravPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(gravPanel);
            panel.add(Box.createVerticalStrut(8));

            // --- Air Resistance ---
            JPanel dragOuterPanel = new JPanel(new BorderLayout(4, 2));
            dragOuterPanel.setBackground(CONTROL_BG);
            dragOuterPanel.setBorder(makeTitledBorder("Air Resistance"));
            dragOuterPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            chkAirResist = new JCheckBox("Enable Drag");
            chkAirResist.setBackground(CONTROL_BG);
            chkAirResist.setForeground(ACCENT_CYAN);
            chkAirResist.setFont(LABEL_FONT);

            sliderDrag = makeSlider(1, 100, 25);
            sliderDrag.setEnabled(false);

            lblDrag = new JLabel("k=0.0025");
            lblDrag.setForeground(ACCENT_YELLOW);
            lblDrag.setFont(HUD_BOLD);
            lblDrag.setHorizontalAlignment(SwingConstants.RIGHT);

            chkAirResist.addActionListener(e -> {
                sliderDrag.setEnabled(chkAirResist.isSelected());
                if (!launched) resetState();
                canvas.repaint();
            });
            sliderDrag.addChangeListener(e -> {
                lblDrag.setText(String.format("k=%.4f", sliderDrag.getValue() * 0.0001));
                if (!launched) resetState();
            });

            JPanel dragSliderRow = new JPanel(new BorderLayout());
            dragSliderRow.setBackground(CONTROL_BG);
            dragSliderRow.add(sliderDrag, BorderLayout.CENTER);
            dragSliderRow.add(lblDrag,    BorderLayout.EAST);

            dragOuterPanel.add(chkAirResist,  BorderLayout.NORTH);
            dragOuterPanel.add(dragSliderRow, BorderLayout.CENTER);
            panel.add(dragOuterPanel);
            panel.add(Box.createVerticalStrut(14));

            // --- Buttons ---
            btnLaunch = makeButton("Launch", ACCENT_CYAN);
            btnReset  = makeButton("Reset",  ACCENT_ORANGE);
            btnLaunch.setAlignmentX(Component.LEFT_ALIGNMENT);
            btnReset.setAlignmentX(Component.LEFT_ALIGNMENT);
            btnLaunch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            btnReset.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            btnLaunch.addActionListener(e -> {
                if (!launched) {
                    launched = true;
                    landed   = false;
                    animTimer.start();
                }
            });
            btnReset.addActionListener(e -> {
                animTimer.stop();
                resetState();
                canvas.repaint();
            });

            panel.add(btnLaunch);
            panel.add(Box.createVerticalStrut(8));
            panel.add(btnReset);
            panel.add(Box.createVerticalGlue());

            // Legend / info
            JTextArea info = new JTextArea(
                "Physics:\n" +
                "  x(t) = v₀cosθ·t\n" +
                "  y(t) = v₀sinθ·t - ½gt²\n" +
                "  Drag: a=-k·|v|·v\n\n" +
                "Integration:\n  Euler  dt=0.016 s\n\n" +
                "Scale:\n  1 px = 0.5 m");
            info.setEditable(false);
            info.setBackground(new Color(0x0d, 0x0d, 0x20));
            info.setForeground(new Color(0x88, 0x88, 0xcc));
            info.setFont(new Font("Monospaced", Font.PLAIN, 10));
            info.setBorder(new EmptyBorder(6, 6, 6, 6));
            info.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(info);

            return panel;
        }

        // ----------------------------------------------------------------
        void resetState() {
            double angleRad = Math.toRadians(sliderAngle != null ? sliderAngle.getValue() : 45);
            double speed    = sliderSpeed   != null ? sliderSpeed.getValue()   : 50;
            gravity         = sliderGravity != null ? sliderGravity.getValue() : 10;

            posX = 0;
            posY = 0;
            velX = speed * Math.cos(angleRad);
            velY = speed * Math.sin(angleRad);
            time = 0;
            maxHeight = 0;
            launched  = false;
            landed    = false;
            trail.clear();
            trail.add(new double[]{posX, posY});

            // Place camera so the launch origin sits in the lower-left of the view
            int cw = (canvas != null && canvas.getWidth()  > 0) ? canvas.getWidth()  : 900;
            int ch = (canvas != null && canvas.getHeight() > 0) ? canvas.getHeight() : 600;
            camX = -(cw / 2.0 - 80)  / PIXELS_PER_METER;  // origin 80 px from left
            camY =  (ch / 2.0 - 70)  / PIXELS_PER_METER;  // origin 70 px from bottom

            if (canvas != null) canvas.repaint();
        }

        // ----------------------------------------------------------------
        void step() {
            if (landed) {
                animTimer.stop();
                return;
            }
            gravity  = sliderGravity.getValue();
            dragCoef = (chkAirResist != null && chkAirResist.isSelected())
                       ? sliderDrag.getValue() * 0.0001
                       : 0.0;

            // Euler integration — quadratic drag opposing velocity
            double speed = Math.hypot(velX, velY);
            double ax = (speed > 0) ? -dragCoef * speed * velX : 0.0;
            double ay = -gravity + ((speed > 0) ? -dragCoef * speed * velY : 0.0);

            velX += ax * DT;
            velY += ay * DT;
            posX += velX * DT;
            posY += velY * DT;
            time += DT;

            if (posY > maxHeight) maxHeight = posY;

            trail.add(new double[]{posX, posY});

            if (posY <= 0 && time > 0.05) {
                posY   = 0;
                velX   = 0;
                velY   = 0;
                landed = true;
                animTimer.stop();
            }

            // Smooth camera follow — lerp toward ball, keeping it centered
            int cw = canvas.getWidth()  > 0 ? canvas.getWidth()  : 900;
            int ch = canvas.getHeight() > 0 ? canvas.getHeight() : 600;
            double targetCamX = posX - (cw / 2.0 - 80) / PIXELS_PER_METER;
            double targetCamY = posY + (ch / 2.0 - 70) / PIXELS_PER_METER;
            camX += (targetCamX - camX) * 0.08;
            camY += (targetCamY - camY) * 0.08;

            canvas.repaint();
        }

        // ----------------------------------------------------------------
        class ProjectileCanvas extends JPanel {

            ProjectileCanvas() {
                setBackground(BG_COLOR);
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Camera-driven origin: screen center shows world point (camX, camY)
                int originX = w / 2 - (int)(camX * PIXELS_PER_METER);
                int originY = h / 2 + (int)(camY * PIXELS_PER_METER);

                drawGrid(g2, w, h, originX, originY);
                drawAxes(g2, w, h, originX, originY);
                drawTrail(g2, originX, originY);
                drawBall(g2, originX, originY);
                drawHUD(g2, w, h);
                drawScaleBar(g2, w, h);

                g2.dispose();
            }

            // Convert physics meters to canvas pixels
            int toPixX(double xMeters, int originX) {
                return originX + (int)(xMeters * PIXELS_PER_METER);
            }
            int toPixY(double yMeters, int originY) {
                return originY - (int)(yMeters * PIXELS_PER_METER);
            }

            void drawGrid(Graphics2D g2, int w, int h, int originX, int originY) {
                g2.setColor(GRID_COLOR);
                g2.setStroke(new BasicStroke(0.5f));
                int gridM  = 10; // meters per grid line
                int gridPx = (int)(gridM * PIXELS_PER_METER);
                if (gridPx < 1) return;

                // Vertical lines: span the full visible width
                int firstGridX = originX % gridPx;
                if (firstGridX > 0) firstGridX -= gridPx;
                for (int px = firstGridX; px < w; px += gridPx) {
                    g2.drawLine(px, 0, px, h);
                }
                // Horizontal lines: only above ground (y >= 0 in world)
                int groundPy = originY; // pixel row of y=0
                int firstGridY = groundPy % gridPx;
                if (firstGridY > 0) firstGridY -= gridPx;
                for (int py = firstGridY; py < groundPy; py += gridPx) {
                    if (py >= 0 && py < h) g2.drawLine(0, py, w, py);
                }
            }

            void drawAxes(Graphics2D g2, int w, int h, int originX, int originY) {
                g2.setColor(AXIS_COLOR);
                g2.setStroke(new BasicStroke(1.5f));

                // Ground line (y=0) — draw across full width if on screen
                if (originY >= 0 && originY <= h) {
                    g2.drawLine(0, originY, w, originY);
                    drawArrow(g2, w - 18, originY, w - 10, originY);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                    g2.setColor(ACCENT_CYAN);
                    g2.drawString("x (m)", w - 44, originY - 6);
                }

                // Y axis (x=0) — draw full height if on screen
                if (originX >= 0 && originX <= w) {
                    g2.setColor(AXIS_COLOR);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawLine(originX, 0, originX, h);
                    drawArrow(g2, originX, 18, originX, 10);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                    g2.setColor(ACCENT_CYAN);
                    g2.drawString("y (m)", originX + 6, 22);
                }

                // Tick labels along the ground
                g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
                g2.setColor(new Color(0x77, 0x77, 0xaa));
                int labelStep = 50; // every 50 m
                // Compute first visible world x multiple of labelStep
                double leftWorldX  = (0 - originX) / PIXELS_PER_METER;
                double rightWorldX = (w - originX) / PIXELS_PER_METER;
                int firstLabelX = (int)(Math.floor(leftWorldX / labelStep)) * labelStep;
                for (int m = firstLabelX; m <= (int)rightWorldX + labelStep; m += labelStep) {
                    if (m < 0) continue;
                    int px = toPixX(m, originX);
                    if (px < 0 || px > w) continue;
                    int tickY = Math.min(Math.max(originY, 0), h - 15);
                    g2.setColor(AXIS_COLOR);
                    if (originY >= 0 && originY <= h) g2.drawLine(px, originY, px, originY + 4);
                    g2.setColor(new Color(0x77, 0x77, 0xaa));
                    g2.drawString(m + "m", px - 10, tickY + 14);
                }

                // Tick labels along the Y axis
                double bottomWorldY = -(originY - h) / PIXELS_PER_METER;
                double topWorldY    = originY / PIXELS_PER_METER;
                int firstLabelY = (int)(Math.floor(bottomWorldY / labelStep)) * labelStep;
                for (int m = firstLabelY; m <= (int)topWorldY + labelStep; m += labelStep) {
                    if (m < 0) continue;
                    int py = toPixY(m, originY);
                    if (py < 0 || py > h) continue;
                    int tickX = Math.min(Math.max(originX, 0), w - 35);
                    g2.setColor(AXIS_COLOR);
                    if (originX >= 0 && originX <= w) g2.drawLine(originX - 4, py, originX, py);
                    g2.setColor(new Color(0x77, 0x77, 0xaa));
                    g2.drawString(m + "m", tickX - 34, py + 4);
                }
            }

            void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2) {
                g2.drawLine(x1, y1, x2, y2);
                double angle = Math.atan2(y2 - y1, x2 - x1);
                int sz = 6;
                int ax1 = (int)(x2 - sz * Math.cos(angle - 0.4));
                int ay1 = (int)(y2 - sz * Math.sin(angle - 0.4));
                int ax2 = (int)(x2 - sz * Math.cos(angle + 0.4));
                int ay2 = (int)(y2 - sz * Math.sin(angle + 0.4));
                g2.drawLine(x2, y2, ax1, ay1);
                g2.drawLine(x2, y2, ax2, ay2);
            }

            void drawTrail(Graphics2D g2, int originX, int originY) {
                int size = trail.size();
                if (size < 2) return;

                // Draw as a fading poly-line: older = more transparent
                for (int i = 1; i < size; i++) {
                    float alpha = (float) i / size;
                    Color trailColor = new Color(
                        ACCENT_CYAN.getRed()   / 255f,
                        ACCENT_CYAN.getGreen() / 255f,
                        ACCENT_CYAN.getBlue()  / 255f,
                        Math.max(0f, Math.min(1f, alpha * 0.8f)));
                    g2.setColor(trailColor);
                    g2.setStroke(new BasicStroke(1.5f));

                    double[] prev = trail.get(i - 1);
                    double[] curr = trail.get(i);
                    g2.drawLine(
                        toPixX(prev[0], originX), toPixY(prev[1], originY),
                        toPixX(curr[0], originX), toPixY(curr[1], originY));
                }

                // Dots every 20 steps
                for (int i = 0; i < size; i += 20) {
                    float alpha = (float) i / size;
                    g2.setColor(new Color(1f, 1f, 0f, alpha * 0.6f));
                    double[] pt = trail.get(i);
                    int px = toPixX(pt[0], originX);
                    int py = toPixY(pt[1], originY);
                    g2.fillOval(px - 2, py - 2, 4, 4);
                }
            }

            void drawBall(Graphics2D g2, int originX, int originY) {
                int px = toPixX(posX, originX);
                int py = toPixY(posY, originY);

                // Glow
                for (int r = 18; r >= 8; r -= 2) {
                    float alpha = (18 - r) / 20f;
                    g2.setColor(new Color(1f, 0.84f, 0f, alpha * 0.3f));
                    g2.fillOval(px - r, py - r, 2 * r, 2 * r);
                }
                // Ball
                g2.setColor(ACCENT_YELLOW);
                g2.fillOval(px - 8, py - 8, 16, 16);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(px - 8, py - 8, 16, 16);

                // Velocity vector — shown before launch and during flight
                if (!landed) {
                    double scale = 0.4;
                    int vx2 = px + (int)(velX * scale * PIXELS_PER_METER);
                    int vy2 = py - (int)(velY * scale * PIXELS_PER_METER);

                    if (!launched) {
                        // Pre-launch: draw faint Vx / Vy component dashes
                        g2.setColor(new Color(0x00, 0xCC, 0xFF, 90));
                        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                            1f, new float[]{4f, 4f}, 0f));
                        g2.drawLine(px, py, vx2, py);   // horizontal component
                        g2.drawLine(vx2, py, vx2, vy2); // vertical component

                        // Component labels
                        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
                        g2.setColor(new Color(0x00, 0xCC, 0xFF, 160));
                        g2.drawString(String.format("Vx=%.1f", velX), px + 4, py - 4);
                        g2.drawString(String.format("Vy=%.1f", velY), vx2 + 4, (py + vy2) / 2);
                    }

                    // Main resultant vector
                    g2.setColor(new Color(0xFF, 0x99, 0x00, launched ? 220 : 180));
                    g2.setStroke(new BasicStroke(launched ? 2f : 1.5f));
                    g2.drawLine(px, py, vx2, vy2);
                    drawArrow(g2, px, py, vx2, vy2);

                    // Speed label on arrow tip
                    double spd = Math.hypot(velX, velY);
                    g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
                    g2.setColor(ACCENT_YELLOW);
                    g2.drawString(String.format("%.1f m/s", spd), vx2 + 4, vy2 - 4);
                }
            }

            void drawHUD(Graphics2D g2, int w, int h) {
                double range = trail.isEmpty() ? 0 : trail.get(trail.size() - 1)[0];

                String[] lines = {
                    "--- PROJECTILE HUD ---",
                    String.format("Time     : %7.3f s",     time),
                    String.format("X        : %7.2f m",     posX),
                    String.format("Y        : %7.2f m",     posY),
                    String.format("Vx       : %7.2f m/s",   velX),
                    String.format("Vy       : %7.2f m/s",   velY),
                    String.format("Speed    : %7.2f m/s",   Math.hypot(velX, velY)),
                    String.format("Max H    : %7.2f m",     maxHeight),
                    String.format("Range    : %7.2f m",     range),
                    String.format("Gravity  : %7.2f m/s²",  gravity),
                    String.format("Angle    : %7.1f deg",   (double)(sliderAngle.getValue())),
                    String.format("Drag k   : %s",          dragCoef > 0
                        ? String.format("%7.4f", dragCoef) : "    OFF"),
                    "",
                    landed   ? "STATUS: LANDED" : (launched ? "STATUS: IN FLIGHT" : "STATUS: READY"),
                };

                int boxX = w - 220;
                int boxY = 14;
                int lineH = 17;
                int boxW = 210;
                int boxH = lines.length * lineH + 14;

                // Semi-transparent background
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRoundRect(boxX - 4, boxY - 4, boxW, boxH, 8, 8);
                g2.setColor(AXIS_COLOR);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(boxX - 4, boxY - 4, boxW, boxH, 8, 8);

                int y = boxY + lineH;
                for (String line : lines) {
                    if (line.startsWith("---")) {
                        g2.setFont(HUD_BOLD);
                        g2.setColor(ACCENT_CYAN);
                    } else if (line.startsWith("STATUS")) {
                        g2.setFont(HUD_BOLD);
                        g2.setColor(landed ? ACCENT_ORANGE : (launched ? ACCENT_YELLOW : Color.GREEN));
                    } else {
                        g2.setFont(HUD_FONT);
                        g2.setColor(HUD_COLOR);
                    }
                    g2.drawString(line, boxX, y);
                    y += lineH;
                }
            }

            void drawScaleBar(Graphics2D g2, int w, int h) {
                // 50 m scale bar
                int barMeters = 50;
                int barPx = (int)(barMeters * PIXELS_PER_METER);
                int bx = 60;
                int by = h - 18;

                g2.setColor(new Color(0x66, 0x66, 0x99));
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(bx, by, bx + barPx, by);
                g2.drawLine(bx, by - 4, bx, by + 4);
                g2.drawLine(bx + barPx, by - 4, bx + barPx, by + 4);
                g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
                g2.setColor(new Color(0x88, 0x88, 0xcc));
                g2.drawString(barMeters + " m", bx + barPx / 2 - 14, by - 6);
            }
        }
    }

    // =======================================================================
    //  TAB 2 — THREE-BODY GRAVITY
    // =======================================================================
    static class ThreeBodyPanel extends JPanel {

        // --- Physics constants ---
        /** Gravitational constant in simulation units. */
        static final double G            = 6.674e-11;
        /** Softening factor to prevent singularity at close encounters (meters). */
        static final double SOFTENING    = 1e8;
        /** RK4 time step in seconds. */
        static final double DT           = 3600.0;   // 1 hour per step
        /** Steps per timer tick (multiple steps per frame for speed). */
        static final int    STEPS_PER_TICK = 24;
        /** Scale for display: meters → pixels. */
        static final double DISPLAY_SCALE  = 1.0 / 5e8; // 1 px = 500 000 km

        // --- Bodies ---
        Body[] bodies;

        // --- Animation state ---
        boolean running = false;
        double  simTime = 0;  // seconds elapsed
        javax.swing.Timer animTimer;

        // --- Preset state ---
        String currentPreset = "Sun-Earth-Moon";

        // --- Sub-panels ---
        ThreeBodyCanvas canvas;
        JPanel          infoPanel;
        JLabel[]        infoLabels;  // mass / vel per body
        JLabel          lblTime;

        // --- Buttons ---
        JButton btnStartPause;
        JButton btnReset;
        JComboBox<String> comboPreset;

        // --- Custom-preset controls ---
        JPanel    customPanel;
        JSlider[] customMassSliders = new JSlider[3];
        JLabel[]  customMassLabels  = new JLabel[3];

        ThreeBodyPanel() {
            setLayout(new BorderLayout(4, 4));
            setBackground(BG_COLOR);

            canvas     = new ThreeBodyCanvas();
            infoPanel  = buildInfoPanel();

            JScrollPane scroll = new JScrollPane(canvas);
            scroll.setBackground(BG_COLOR);
            scroll.getViewport().setBackground(BG_COLOR);
            scroll.setBorder(BorderFactory.createLineBorder(AXIS_COLOR, 1));

            add(scroll,    BorderLayout.CENTER);
            add(infoPanel, BorderLayout.EAST);

            loadPreset(currentPreset);

            animTimer = new javax.swing.Timer(16, e -> tick());
            animTimer.setCoalesce(true);
        }

        void pauseTimer() {
            if (running) {
                running = false;
                animTimer.stop();
                btnStartPause.setText("  Start  ");
            }
        }

        // ----------------------------------------------------------------
        JPanel buildInfoPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(CONTROL_BG);
            panel.setBorder(new EmptyBorder(12, 12, 12, 12));
            panel.setPreferredSize(new Dimension(260, 0));

            JLabel title = new JLabel("Three-Body Gravity");
            title.setForeground(ACCENT_CYAN);
            title.setFont(new Font("SansSerif", Font.BOLD, 14));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(title);
            panel.add(Box.createVerticalStrut(10));

            // Preset selector
            JPanel presetPanel = new JPanel(new BorderLayout());
            presetPanel.setBackground(CONTROL_BG);
            presetPanel.setBorder(makeTitledBorder("Preset"));
            presetPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            comboPreset = new JComboBox<>(new String[]{
                "Sun-Earth-Moon", "Figure-8 Orbit", "Random", "Custom"});
            comboPreset.setBackground(new Color(0x22, 0x22, 0x44));
            comboPreset.setForeground(Color.WHITE);
            comboPreset.setFont(LABEL_FONT);
            presetPanel.add(comboPreset, BorderLayout.CENTER);
            panel.add(presetPanel);
            panel.add(Box.createVerticalStrut(6));

            // ----- Custom mass panel (visible only for "Custom" preset) -----
            customPanel = new JPanel();
            customPanel.setLayout(new BoxLayout(customPanel, BoxLayout.Y_AXIS));
            customPanel.setBackground(CONTROL_BG);
            customPanel.setBorder(makeTitledBorder("Custom Masses"));
            customPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            customPanel.setVisible(false);

            Color[] bodyColors = {new Color(0xFF, 0xD7, 0x00), new Color(0x00, 0xE5, 0xFF), new Color(0xFF, 0x6B, 0x35)};
            String[] bodyNames = {"Alpha", "Beta", "Gamma"};
            int[]    defaults  = {10, 10, 10}; // default: 1.0 Msun each (slider * 0.1)

            for (int i = 0; i < 3; i++) {
                final int idx = i;
                JPanel row = new JPanel(new BorderLayout(4, 0));
                row.setBackground(CONTROL_BG);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

                JLabel nameLbl = new JLabel(bodyNames[i]);
                nameLbl.setForeground(bodyColors[i]);
                nameLbl.setFont(HUD_BOLD);
                nameLbl.setPreferredSize(new Dimension(42, 18));

                customMassSliders[i] = makeSlider(1, 100, defaults[i]);
                customMassLabels[i]  = new JLabel("1.0 M☉");
                customMassLabels[i].setForeground(ACCENT_YELLOW);
                customMassLabels[i].setFont(HUD_FONT);
                customMassLabels[i].setPreferredSize(new Dimension(54, 18));
                customMassLabels[i].setHorizontalAlignment(SwingConstants.RIGHT);

                customMassSliders[i].addChangeListener(e -> {
                    double msun = customMassSliders[idx].getValue() * 0.1;
                    customMassLabels[idx].setText(String.format("%.1f M☉", msun));
                    if ("Custom".equals(currentPreset) && !running) {
                        loadCustom();
                        updateInfoLabels();
                        canvas.repaint();
                    }
                });

                row.add(nameLbl,              BorderLayout.WEST);
                row.add(customMassSliders[i], BorderLayout.CENTER);
                row.add(customMassLabels[i],  BorderLayout.EAST);
                customPanel.add(row);
                if (i < 2) customPanel.add(Box.createVerticalStrut(2));
            }

            panel.add(customPanel);
            panel.add(Box.createVerticalStrut(6));

            // Wire preset combo AFTER customPanel is built
            comboPreset.addActionListener(e -> {
                currentPreset = (String) comboPreset.getSelectedItem();
                customPanel.setVisible("Custom".equals(currentPreset));
                animTimer.stop();
                running = false;
                btnStartPause.setText("  Start  ");
                loadPreset(currentPreset);
            });

            panel.add(Box.createVerticalStrut(4));

            // Elapsed time
            lblTime = new JLabel("Time: 0.00 days");
            lblTime.setForeground(ACCENT_YELLOW);
            lblTime.setFont(HUD_BOLD);
            lblTime.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(lblTime);
            panel.add(Box.createVerticalStrut(10));

            // Per-body info labels (3 bodies, 3 labels each: name/mass, vel, blank)
            infoLabels = new JLabel[9];
            Color[] infoColors = {new Color(0xFF, 0xD7, 0x00), new Color(0x00, 0xE5, 0xFF), new Color(0xFF, 0x6B, 0x35)};
            for (int i = 0; i < 3; i++) {
                JPanel bp = new JPanel();
                bp.setLayout(new BoxLayout(bp, BoxLayout.Y_AXIS));
                bp.setBackground(new Color(0x10, 0x10, 0x28));
                bp.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(infoColors[i].darker(), 1),
                    new EmptyBorder(4, 6, 4, 6)));
                bp.setAlignmentX(Component.LEFT_ALIGNMENT);
                bp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

                infoLabels[i * 3]     = new JLabel("Body " + (i+1));
                infoLabels[i * 3 + 1] = new JLabel("Mass: ---");
                infoLabels[i * 3 + 2] = new JLabel("Vel:  ---");
                for (int j = 0; j < 3; j++) {
                    infoLabels[i * 3 + j].setForeground(j == 0 ? infoColors[i] : Color.LIGHT_GRAY);
                    infoLabels[i * 3 + j].setFont(j == 0 ? HUD_BOLD : HUD_FONT);
                    infoLabels[i * 3 + j].setAlignmentX(Component.LEFT_ALIGNMENT);
                    bp.add(infoLabels[i * 3 + j]);
                }
                panel.add(bp);
                panel.add(Box.createVerticalStrut(6));
            }

            panel.add(Box.createVerticalStrut(14));

            // Buttons
            btnStartPause = makeButton("  Start  ", Color.GREEN);
            btnReset      = makeButton("  Reset  ", ACCENT_ORANGE);
            btnStartPause.setAlignmentX(Component.LEFT_ALIGNMENT);
            btnReset.setAlignmentX(Component.LEFT_ALIGNMENT);
            btnStartPause.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            btnReset.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            btnStartPause.addActionListener(e -> {
                running = !running;
                if (running) {
                    animTimer.start();
                    btnStartPause.setText("  Pause  ");
                } else {
                    animTimer.stop();
                    btnStartPause.setText("  Start  ");
                }
            });
            btnReset.addActionListener(e -> {
                animTimer.stop();
                running = false;
                btnStartPause.setText("  Start  ");
                loadPreset(currentPreset);
            });

            panel.add(btnStartPause);
            panel.add(Box.createVerticalStrut(6));
            panel.add(btnReset);
            panel.add(Box.createVerticalGlue());

            // Method note
            JTextArea note = new JTextArea(
                "Integration: RK4\ndt = 1 hour/step\n24 steps/frame\n\n" +
                "Forces: Newtonian\ngravity with\nsoftening ε");
            note.setEditable(false);
            note.setBackground(new Color(0x0d, 0x0d, 0x20));
            note.setForeground(new Color(0x66, 0x66, 0x99));
            note.setFont(new Font("Monospaced", Font.PLAIN, 10));
            note.setBorder(new EmptyBorder(6, 6, 6, 6));
            note.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(note);

            return panel;
        }

        // ----------------------------------------------------------------
        void loadPreset(String name) {
            simTime = 0;
            switch (name) {
                case "Figure-8 Orbit": loadFigure8();      break;
                case "Random":         loadRandom();        break;
                case "Custom":         loadCustom();        break;
                default:               loadSunEarthMoon();  break;
            }
            updateInfoLabels();
            if (canvas != null) canvas.repaint();
        }

        void loadCustom() {
            double Msun = 1.989e30;
            double AU   = 1.5e11;
            double R    = 1.2 * AU; // equilateral triangle radius

            // Read masses from sliders (each tick = 0.1 Msun)
            double[] ms = new double[3];
            for (int i = 0; i < 3; i++) {
                ms[i] = Msun * (customMassSliders[i] != null
                    ? customMassSliders[i].getValue() * 0.1 : 1.0);
            }

            // Equilateral triangle positions (center-of-mass corrected)
            double[] xs = {R, -R * 0.5, -R * 0.5};
            double[] ys = {0,  R * Math.sqrt(3) / 2, -R * Math.sqrt(3) / 2};
            double totalM = ms[0] + ms[1] + ms[2];
            double cmx = (ms[0]*xs[0] + ms[1]*xs[1] + ms[2]*xs[2]) / totalM;
            double cmy = (ms[0]*ys[0] + ms[1]*ys[1] + ms[2]*ys[2]) / totalM;
            for (int i = 0; i < 3; i++) { xs[i] -= cmx; ys[i] -= cmy; }

            // Initial velocities: each body orbits the CM of the other two
            double[][] vels = new double[3][2];
            for (int i = 0; i < 3; i++) {
                int j = (i + 1) % 3, k = (i + 2) % 3;
                double otherM  = ms[j] + ms[k];
                double otherCMx = (ms[j]*xs[j] + ms[k]*xs[k]) / otherM;
                double otherCMy = (ms[j]*ys[j] + ms[k]*ys[k]) / otherM;
                double dx = xs[i] - otherCMx, dy = ys[i] - otherCMy;
                double d  = Math.sqrt(dx*dx + dy*dy);
                double v  = Math.sqrt(G * otherM / d);
                vels[i][0] = -v * dy / d;  // tangential
                vels[i][1] =  v * dx / d;
            }

            // Shift to zero total momentum
            double vCMx = 0, vCMy = 0;
            for (int i = 0; i < 3; i++) { vCMx += ms[i]*vels[i][0]; vCMy += ms[i]*vels[i][1]; }
            vCMx /= totalM; vCMy /= totalM;
            for (int i = 0; i < 3; i++) { vels[i][0] -= vCMx; vels[i][1] -= vCMy; }

            Color[]  cols  = {new Color(0xFF,0xD7,0x00), new Color(0x00,0xE5,0xFF), new Color(0xFF,0x6B,0x35)};
            String[] names = {"Alpha", "Beta", "Gamma"};
            bodies = new Body[3];
            for (int i = 0; i < 3; i++) {
                int radius = (int) Math.min(20, Math.max(5, ms[i] / Msun * 10));
                bodies[i] = new Body(names[i], cols[i], ms[i], radius,
                    new Vector2D(xs[i], ys[i]),
                    new Vector2D(vels[i][0], vels[i][1]));
            }
            simTime = 0;
        }

        /**
         * Classic figure-8 three-body choreography (Chenciner & Montgomery 2000).
         * Units: AU for length, years for time, solar masses for mass.
         * We work in SI here; scale chosen so the figure-8 fits the canvas.
         */
        void loadFigure8() {
            // Scaled to use AU = 1.496e11 m, year = 3.156e7 s, M_sun = 1.989e30 kg
            // Figure-8 initial conditions (dimensionless, from literature):
            // Positions: body1=(-0.97000436, 0.24308753), body2=(0,0), body3=(0.97000436,-0.24308753)
            // Velocities: v3=(0.93240737/2, 0.86473146/2) * scale, body1=body2=-v3/2 each...
            // Use a convenient AU/year unit scaled to pixels
            double AU   = 1.5e11;     // 1 AU in meters
            double yr   = 3.156e7;    // 1 year in seconds
            double Msun = 2e30;

            double x1 = -0.97000436 * AU;
            double y1 =  0.24308753 * AU;
            double x2 =  0.0;
            double y2 =  0.0;
            double x3 =  0.97000436 * AU;
            double y3 = -0.24308753 * AU;

            // Velocity scale factor for AU/year
            double vscale = AU / yr;
            double vx3 =  0.93240737 * vscale;
            double vy3 =  0.86473146 * vscale;
            double vx1 = -vx3 / 2.0;
            double vy1 = -vy3 / 2.0;
            double vx2 = -vx3 / 2.0;
            double vy2 = -vy3 / 2.0;

            double m = Msun;

            bodies = new Body[]{
                new Body("Alpha", new Color(0xFF, 0xD7, 0x00), m, 10,
                    new Vector2D(x1, y1), new Vector2D(vx1, vy1)),
                new Body("Beta",  new Color(0x00, 0xE5, 0xFF), m, 10,
                    new Vector2D(x2, y2), new Vector2D(vx2, vy2)),
                new Body("Gamma", new Color(0xFF, 0x6B, 0x35), m, 10,
                    new Vector2D(x3, y3), new Vector2D(vx3, vy3)),
            };
        }

        void loadSunEarthMoon() {
            double Msun   = 1.989e30;
            double Mearth = 5.972e24;
            double Mmoon  = 7.342e22;

            // Sun at origin, Earth ~1 AU away, Moon ~384400 km from Earth
            double AU       = 1.496e11;
            double earthOrb = AU;           // Earth orbital radius
            double moonOrb  = 3.844e8;      // Moon orbital radius from Earth

            // Circular orbit velocities
            double vEarth = Math.sqrt(G * Msun / earthOrb);               // ~29.8 km/s
            double vMoon  = Math.sqrt(G * Mearth / moonOrb) + vEarth;     // Moon around Earth + Earth's velocity

            bodies = new Body[]{
                new Body("Sun",   new Color(0xFF, 0xD7, 0x00), Msun,   18,
                    new Vector2D(0, 0),
                    new Vector2D(0, 0)),
                new Body("Earth", new Color(0x00, 0xE5, 0xFF), Mearth, 8,
                    new Vector2D(earthOrb, 0),
                    new Vector2D(0, vEarth)),
                new Body("Moon",  new Color(0xCC, 0xCC, 0xCC), Mmoon,  4,
                    new Vector2D(earthOrb + moonOrb, 0),
                    new Vector2D(0, vMoon)),
            };
        }

        void loadRandom() {
            double Msun = 1.989e30;
            double AU   = 1.5e11;
            java.util.Random rng = new java.util.Random(System.currentTimeMillis());

            Color[] cols = {new Color(0xFF, 0xD7, 0x00), new Color(0x00, 0xE5, 0xFF), new Color(0xFF, 0x6B, 0x35)};
            String[] names = {"Alpha", "Beta", "Gamma"};
            bodies = new Body[3];
            for (int i = 0; i < 3; i++) {
                double m  = Msun * (0.5 + rng.nextDouble() * 2.0);
                double px = (rng.nextDouble() - 0.5) * 3.0 * AU;
                double py = (rng.nextDouble() - 0.5) * 3.0 * AU;
                double vx = (rng.nextDouble() - 0.5) * 2e4;
                double vy = (rng.nextDouble() - 0.5) * 2e4;
                bodies[i] = new Body(names[i], cols[i], m, 10,
                    new Vector2D(px, py), new Vector2D(vx, vy));
            }
        }

        // ----------------------------------------------------------------
        void tick() {
            for (int s = 0; s < STEPS_PER_TICK; s++) {
                rk4Step();
                simTime += DT;
                for (Body b : bodies) b.recordTrail();
            }
            updateInfoLabels();
            canvas.repaint();
        }

        // ----------------------------------------------------------------
        // RK4 Integration
        // ----------------------------------------------------------------

        /**
         * Computes the gravitational acceleration on body[i] due to all others.
         * positions[] is a flat [x0,y0, x1,y1, x2,y2, ...] array.
         */
        double[] accel(double[] positions, int bodyIdx) {
            double ax = 0, ay = 0;
            double xi = positions[bodyIdx * 2];
            double yi = positions[bodyIdx * 2 + 1];
            for (int j = 0; j < bodies.length; j++) {
                if (j == bodyIdx) continue;
                double xj = positions[j * 2];
                double yj = positions[j * 2 + 1];
                double dx = xj - xi;
                double dy = yj - yi;
                double distSq = dx * dx + dy * dy + SOFTENING * SOFTENING;
                double dist   = Math.sqrt(distSq);
                double factor = G * bodies[j].getMass() / (distSq * dist);
                ax += factor * dx;
                ay += factor * dy;
            }
            return new double[]{ax, ay};
        }

        void rk4Step() {
            int n = bodies.length;

            // State: positions and velocities
            double[] pos = new double[n * 2];
            double[] vel = new double[n * 2];
            for (int i = 0; i < n; i++) {
                pos[i * 2]     = bodies[i].getPosition().x;
                pos[i * 2 + 1] = bodies[i].getPosition().y;
                vel[i * 2]     = bodies[i].getVelocity().x;
                vel[i * 2 + 1] = bodies[i].getVelocity().y;
            }

            // k1
            double[] k1v = new double[n * 2];
            double[] k1a = new double[n * 2];
            for (int i = 0; i < n; i++) {
                k1v[i * 2]     = vel[i * 2];
                k1v[i * 2 + 1] = vel[i * 2 + 1];
                double[] a = accel(pos, i);
                k1a[i * 2]     = a[0];
                k1a[i * 2 + 1] = a[1];
            }

            // k2 positions/velocities at half-step
            double[] pos2 = new double[n * 2];
            double[] vel2 = new double[n * 2];
            for (int i = 0; i < n * 2; i++) {
                pos2[i] = pos[i] + 0.5 * DT * k1v[i];
                vel2[i] = vel[i] + 0.5 * DT * k1a[i];
            }
            double[] k2v = new double[n * 2];
            double[] k2a = new double[n * 2];
            for (int i = 0; i < n; i++) {
                k2v[i * 2]     = vel2[i * 2];
                k2v[i * 2 + 1] = vel2[i * 2 + 1];
                double[] a = accel(pos2, i);
                k2a[i * 2]     = a[0];
                k2a[i * 2 + 1] = a[1];
            }

            // k3
            double[] pos3 = new double[n * 2];
            double[] vel3 = new double[n * 2];
            for (int i = 0; i < n * 2; i++) {
                pos3[i] = pos[i] + 0.5 * DT * k2v[i];
                vel3[i] = vel[i] + 0.5 * DT * k2a[i];
            }
            double[] k3v = new double[n * 2];
            double[] k3a = new double[n * 2];
            for (int i = 0; i < n; i++) {
                k3v[i * 2]     = vel3[i * 2];
                k3v[i * 2 + 1] = vel3[i * 2 + 1];
                double[] a = accel(pos3, i);
                k3a[i * 2]     = a[0];
                k3a[i * 2 + 1] = a[1];
            }

            // k4
            double[] pos4 = new double[n * 2];
            double[] vel4 = new double[n * 2];
            for (int i = 0; i < n * 2; i++) {
                pos4[i] = pos[i] + DT * k3v[i];
                vel4[i] = vel[i] + DT * k3a[i];
            }
            double[] k4v = new double[n * 2];
            double[] k4a = new double[n * 2];
            for (int i = 0; i < n; i++) {
                k4v[i * 2]     = vel4[i * 2];
                k4v[i * 2 + 1] = vel4[i * 2 + 1];
                double[] a = accel(pos4, i);
                k4a[i * 2]     = a[0];
                k4a[i * 2 + 1] = a[1];
            }

            // Combine
            for (int i = 0; i < n; i++) {
                double newPx = pos[i*2]   + (DT/6.0)*(k1v[i*2]   + 2*k2v[i*2]   + 2*k3v[i*2]   + k4v[i*2]);
                double newPy = pos[i*2+1] + (DT/6.0)*(k1v[i*2+1] + 2*k2v[i*2+1] + 2*k3v[i*2+1] + k4v[i*2+1]);
                double newVx = vel[i*2]   + (DT/6.0)*(k1a[i*2]   + 2*k2a[i*2]   + 2*k3a[i*2]   + k4a[i*2]);
                double newVy = vel[i*2+1] + (DT/6.0)*(k1a[i*2+1] + 2*k2a[i*2+1] + 2*k3a[i*2+1] + k4a[i*2+1]);
                bodies[i].setPosition(new Vector2D(newPx, newPy));
                bodies[i].setVelocity(new Vector2D(newVx, newVy));
            }
        }

        // ----------------------------------------------------------------
        void updateInfoLabels() {
            if (bodies == null || infoLabels == null) return;

            double days = simTime / 86400.0;
            String timeStr;
            if (days > 365.25) {
                timeStr = String.format("Time: %.2f yr", days / 365.25);
            } else {
                timeStr = String.format("Time: %.2f days", days);
            }
            lblTime.setText(timeStr);

            for (int i = 0; i < bodies.length; i++) {
                Body b = bodies[i];
                double mass = b.getMass();
                double vel  = b.getVelocity().magnitude();

                String massStr;
                if (mass >= 1e27) massStr = String.format("%.2e Msun", mass / 1.989e30);
                else              massStr = String.format("%.2e kg",   mass);

                String velStr;
                if (vel >= 1000) velStr = String.format("%.2f km/s", vel / 1000.0);
                else             velStr = String.format("%.2f m/s",  vel);

                infoLabels[i * 3].setText(b.getName());
                infoLabels[i * 3 + 1].setText("Mass: " + massStr);
                infoLabels[i * 3 + 2].setText("Vel:  " + velStr);
            }
        }

        // ----------------------------------------------------------------
        class ThreeBodyCanvas extends JPanel {

            static final int CANVAS_W = 1000;
            static final int CANVAS_H = 700;

            ThreeBodyCanvas() {
                setPreferredSize(new Dimension(CANVAS_W, CANVAS_H));
                setBackground(BG_COLOR);
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (bodies == null) return;

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Center of canvas in pixels
                int cx = w / 2;
                int cy = h / 2;

                // Compute dynamic scale: find bounding box of all body positions + trail tips
                double scale = computeScale(w, h);

                drawGrid(g2, w, h, cx, cy, scale);
                drawAxes(g2, w, h, cx, cy);
                drawTrails(g2, cx, cy, scale);
                drawBodies(g2, cx, cy, scale);
                drawHUD(g2, w, h, scale);

                g2.dispose();
            }

            // Auto-scale: fit all bodies on screen with 20% margin
            double computeScale(int w, int h) {
                double maxR = 1e6; // minimum extent
                for (Body b : bodies) {
                    double r = b.getPosition().magnitude();
                    if (r > maxR) maxR = r;
                    // Also consider trail extent
                    Vector2D[] trail = b.getTrailSnapshot();
                    for (Vector2D pt : trail) {
                        double tr = pt.magnitude();
                        if (tr > maxR) maxR = tr;
                    }
                }
                double marginFactor = 0.40;  // 40% margin
                double halfSize = Math.min(w, h) / 2.0;
                return halfSize * (1.0 - marginFactor) / maxR;
            }

            int toPixX(double xMeters, int cx, double scale) {
                return cx + (int)(xMeters * scale);
            }
            int toPixY(double yMeters, int cy, double scale) {
                return cy - (int)(yMeters * scale);
            }

            void drawGrid(Graphics2D g2, int w, int h, int cx, int cy, double scale) {
                g2.setColor(GRID_COLOR);
                g2.setStroke(new BasicStroke(0.5f));

                // Draw concentric circles as guide rings
                double[] radii = {0.5e11, 1e11, 2e11, 4e11, 8e11, 1.5e12, 3e12};
                for (double rMeters : radii) {
                    int rPx = (int)(rMeters * scale);
                    if (rPx < 4 || rPx > Math.max(w, h)) continue;
                    g2.drawOval(cx - rPx, cy - rPx, rPx * 2, rPx * 2);
                }

                // Cross-hair lines
                g2.setColor(new Color(0x22, 0x22, 0x44));
                g2.drawLine(cx, 0, cx, h);
                g2.drawLine(0, cy, w, cy);
            }

            void drawAxes(Graphics2D g2, int w, int h, int cx, int cy) {
                g2.setColor(AXIS_COLOR);
                g2.setStroke(new BasicStroke(1f));
                // Minimal axis ticks
                int tickLen = 5;
                g2.drawLine(cx - tickLen, cy, cx + tickLen, cy);
                g2.drawLine(cx, cy - tickLen, cx, cy + tickLen);
                // Label center
                g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
                g2.setColor(new Color(0x55, 0x55, 0x88));
                g2.drawString("0,0", cx + 4, cy - 4);
            }

            void drawTrails(Graphics2D g2, int cx, int cy, double scale) {
                for (Body b : bodies) {
                    Vector2D[] trail = b.getTrailSnapshot();
                    if (trail.length < 2) continue;

                    Color col = b.getColor();
                    g2.setStroke(new BasicStroke(1.0f));

                    for (int i = 1; i < trail.length; i++) {
                        float alpha = (float) i / trail.length;
                        g2.setColor(new Color(
                            col.getRed()   / 255f,
                            col.getGreen() / 255f,
                            col.getBlue()  / 255f,
                            Math.max(0f, Math.min(1f, alpha * 0.75f))));

                        int x1 = toPixX(trail[i-1].x, cx, scale);
                        int y1 = toPixY(trail[i-1].y, cy, scale);
                        int x2 = toPixX(trail[i].x, cx, scale);
                        int y2 = toPixY(trail[i].y, cy, scale);
                        g2.drawLine(x1, y1, x2, y2);
                    }
                }
            }

            void drawBodies(Graphics2D g2, int cx, int cy, double scale) {
                for (Body b : bodies) {
                    int px = toPixX(b.getPosition().x, cx, scale);
                    int py = toPixY(b.getPosition().y, cy, scale);
                    int r  = (int) b.getRadius();
                    Color col = b.getColor();

                    // Glow rings
                    for (int gr = r + 14; gr >= r + 4; gr -= 2) {
                        float alpha = (r + 14 - gr) / 20f;
                        g2.setColor(new Color(
                            col.getRed()   / 255f,
                            col.getGreen() / 255f,
                            col.getBlue()  / 255f,
                            alpha * 0.25f));
                        g2.fillOval(px - gr, py - gr, gr * 2, gr * 2);
                    }
                    // Body
                    g2.setColor(col);
                    g2.fillOval(px - r, py - r, r * 2, r * 2);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawOval(px - r, py - r, r * 2, r * 2);

                    // Velocity vector
                    Vector2D vel = b.getVelocity();
                    double velMag = vel.magnitude();
                    if (velMag > 0) {
                        double arrowLen = Math.min(velMag * scale * DT * 10, 60);
                        int vx2 = px + (int)(vel.x / velMag * arrowLen);
                        int vy2 = py - (int)(vel.y / velMag * arrowLen);
                        g2.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 160));
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.drawLine(px, py, vx2, vy2);
                    }

                    // Name label
                    g2.setFont(HUD_BOLD);
                    g2.setColor(col);
                    g2.drawString(b.getName(), px + r + 4, py - r);
                }
            }

            void drawHUD(Graphics2D g2, int w, int h, double scale) {
                double days = simTime / 86400.0;
                String timeStr;
                if (days > 365.25 * 2) {
                    timeStr = String.format("%.3f yr", days / 365.25);
                } else {
                    timeStr = String.format("%.2f days", days);
                }

                // Scale bar: find a nice round number
                double barMeters = pickScaleBarLength(scale, w);
                int barPx = (int)(barMeters * scale);
                barPx = Math.max(barPx, 10);

                String barLabel;
                if (barMeters >= 1.496e11 * 0.5) {
                    barLabel = String.format("%.1f AU", barMeters / 1.496e11);
                } else if (barMeters >= 1e9) {
                    barLabel = String.format("%.0f Mkm", barMeters / 1e9);
                } else {
                    barLabel = String.format("%.0f km", barMeters / 1e3);
                }

                // Scale bar
                int bx = 50;
                int by = h - 22;
                g2.setColor(new Color(0x66, 0x66, 0x99));
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(bx, by, bx + barPx, by);
                g2.drawLine(bx, by - 4, bx, by + 4);
                g2.drawLine(bx + barPx, by - 4, bx + barPx, by + 4);
                g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
                g2.setColor(new Color(0x88, 0x88, 0xcc));
                g2.drawString(barLabel, bx + barPx / 2 - 20, by - 7);

                // Top-left sim info
                String[] hudLines = {
                    "--- THREE-BODY SIM ---",
                    "Preset : " + currentPreset,
                    "Time   : " + timeStr,
                    "dt     : 1 hr/step",
                    "Method : RK4",
                };
                int boxX = 10;
                int boxY = 10;
                int lineH = 16;
                int boxW = 190;
                int boxH = hudLines.length * lineH + 10;

                g2.setColor(new Color(0, 0, 0, 150));
                g2.fillRoundRect(boxX, boxY, boxW, boxH, 8, 8);
                g2.setColor(AXIS_COLOR);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(boxX, boxY, boxW, boxH, 8, 8);

                int ty = boxY + lineH;
                for (String line : hudLines) {
                    if (line.startsWith("---")) {
                        g2.setFont(HUD_BOLD);
                        g2.setColor(ACCENT_CYAN);
                    } else {
                        g2.setFont(HUD_FONT);
                        g2.setColor(HUD_COLOR);
                    }
                    g2.drawString(line, boxX + 6, ty);
                    ty += lineH;
                }
            }

            double pickScaleBarLength(double scale, int w) {
                // We want a bar of roughly w/5 pixels
                double targetPx = w / 5.0;
                double targetMeters = targetPx / scale;
                // Round to nearest power-of-10 or 0.5 * power-of-10
                double log = Math.log10(targetMeters);
                double floor = Math.pow(10, Math.floor(log));
                if (targetMeters / floor >= 5) return floor * 5;
                if (targetMeters / floor >= 2) return floor * 2;
                return floor;
            }
        }
    }

    // =======================================================================
    // Entry point
    // =======================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {}

            // Global dark UI overrides
            UIManager.put("TabbedPane.background",       BG_COLOR);
            UIManager.put("TabbedPane.foreground",       Color.WHITE);
            UIManager.put("TabbedPane.selected",         new Color(0x22, 0x22, 0x44));
            UIManager.put("TabbedPane.selectedForeground", ACCENT_CYAN);
            UIManager.put("TabbedPane.contentAreaColor", BG_COLOR);
            UIManager.put("Panel.background",            BG_COLOR);
            UIManager.put("ScrollPane.background",       BG_COLOR);
            UIManager.put("Viewport.background",         BG_COLOR);
            UIManager.put("ScrollBar.background",        new Color(0x10, 0x10, 0x20));
            UIManager.put("ScrollBar.thumb",             new Color(0x33, 0x33, 0x55));
            UIManager.put("ScrollBar.track",             new Color(0x10, 0x10, 0x20));
            UIManager.put("ComboBox.background",         new Color(0x22, 0x22, 0x44));
            UIManager.put("ComboBox.foreground",         Color.WHITE);
            UIManager.put("ComboBox.selectionBackground", new Color(0x33, 0x33, 0x66));
            UIManager.put("ComboBox.selectionForeground", Color.WHITE);
            UIManager.put("List.background",             new Color(0x22, 0x22, 0x44));
            UIManager.put("List.foreground",             Color.WHITE);

            new PhysicsDemo();
        });
    }
}
