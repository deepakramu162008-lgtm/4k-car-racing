import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;


public class AICarRacingGame extends JPanel implements ActionListener, KeyListener {
    public static final int SCREEN_WIDTH = 1100;
    public static final int SCREEN_HEIGHT = 750;
    private static final int TOTAL_LAPS = 3;

    private static final Point2D.Double[] WAYPOINTS = new Point2D.Double[] {
        new Point2D.Double(220, 160),
        new Point2D.Double(450, 130),
        new Point2D.Double(700, 130),
        new Point2D.Double(920, 200),
        new Point2D.Double(950, 380),
        new Point2D.Double(880, 520),
        new Point2D.Double(720, 480),
        new Point2D.Double(580, 620),
        new Point2D.Double(420, 640),
        new Point2D.Double(260, 600),
        new Point2D.Double(140, 450),
        new Point2D.Double(140, 280)
    };

    private static final double TRACK_ROAD_WIDTH = 110.0;

    // Game state
    private final Timer gameLoopTimer;
    private final List<Car> allCars = new ArrayList<>();
    private Car playerCar;
    private boolean showSensors = true;
    private boolean raceOver = false;
    private long raceStartTime = 0;
    private long raceTimeElapsed = 0;

    // Key inputs
    private boolean keyUp, keyDown, keyLeft, keyRight;

    public AICarRacingGame() {
        setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        setBackground(new Color(34, 139, 34)); // Grass green
        setFocusable(true);
        addKeyListener(this);

        initRace();

        // 60 FPS Game Loop
        gameLoopTimer = new Timer(16, this);
        gameLoopTimer.start();
    }

    private void initRace() {
        allCars.clear();
        raceOver = false;
        raceStartTime = System.currentTimeMillis();
        raceTimeElapsed = 0;

        // Starting grid line setup near Waypoint 0
        Point2D.Double p0 = WAYPOINTS[0];
        Point2D.Double p1 = WAYPOINTS[1];
        double initialAngle = Math.atan2(p1.y - p0.y, p1.x - p0.x);

        // Player Car (Cyan / Blue)
        playerCar = new Car("Player (You)", 190, 140, initialAngle, new Color(0, 180, 255), false);
        playerCar.maxSpeed = 7.5;
        allCars.add(playerCar);

        // AI Car 1 (Red Comet)
        Car ai1 = new Car("AI - Blaze", 230, 180, initialAngle, new Color(230, 40, 40), true);
        ai1.maxSpeed = 7.3;
        ai1.steerSpeed = 0.055;
        allCars.add(ai1);

        // AI Car 2 (Gold Striker)
        Car ai2 = new Car("AI - Falcon", 160, 150, initialAngle, new Color(255, 190, 0), true);
        ai2.maxSpeed = 7.1;
        ai2.steerSpeed = 0.052;
        allCars.add(ai2);

        // AI Car 3 (Neon Viper)
        Car ai3 = new Car("AI - Viper", 200, 190, initialAngle, new Color(50, 230, 80), true);
        ai3.maxSpeed = 7.4;
        ai3.steerSpeed = 0.057;
        allCars.add(ai3);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!raceOver) {
            raceTimeElapsed = System.currentTimeMillis() - raceStartTime;
        }

        // 1. Process player manual or AI inputs
        if (!playerCar.isAI) {
            playerCar.accelerate = keyUp;
            playerCar.brake = keyDown;
            playerCar.turnLeft = keyLeft;
            playerCar.turnRight = keyRight;
        } else {
            updateCarAI(playerCar);
        }

        for (Car car : allCars) {
            if (car != playerCar) {
                updateCarAI(car);
            }
        }

        // 3. Update physics and lap progress for each car
        for (Car car : allCars) {
            car.updatePhysics();
            car.updateProgress(WAYPOINTS);
            if (car.lapCount > TOTAL_LAPS) {
                car.lapCount = TOTAL_LAPS;
                if (!car.hasFinished) {
                    car.hasFinished = true;
                    car.finishTime = raceTimeElapsed;
                }
            }
        }

        // 4. Car-to-car elastic push collision
        for (int i = 0; i < allCars.size(); i++) {
            for (int j = i + 1; j < allCars.size(); j++) {
                resolveCarCollision(allCars.get(i), allCars.get(j));
            }
        }

        // 5. Update race positions
        updateRankings();

        if (playerCar.hasFinished) {
            raceOver = true;
        }

        repaint();
    }

    /**
     * Autonomous AI decision logic using waypoint direction + raycast distance sensors.
     */
    private void updateCarAI(Car car) {
        Point2D.Double targetWp = WAYPOINTS[car.targetWaypointIndex];

        // Angle to next waypoint
        double desiredAngle = Math.atan2(targetWp.y - car.y, targetWp.x - car.x);
        double angleDiff = normalizeAngle(desiredAngle - car.angle);

        // AI Raycast Distance Sensors: 5 rays (front, front-left, front-right, left, right)
        double[] rayAngles = new double[] { 0, -0.4, 0.4, -0.85, 0.85 };
        double[] rayDistances = new double[5];
        double steerAvoidanceForce = 0;

        for (int i = 0; i < rayAngles.length; i++) {
            double rayAngle = car.angle + rayAngles[i];
            double dist = castRay(car.x, car.y, rayAngle, 120.0, car);
            rayDistances[i] = dist;

            // Danger threshold
            if (dist < 100.0) {
                double weight = (100.0 - dist) / 100.0;
                // If danger on left, steer right (+); if on right, steer left (-)
                if (rayAngles[i] < 0) {
                    steerAvoidanceForce += weight * 0.7; // steer right
                } else if (rayAngles[i] > 0) {
                    steerAvoidanceForce -= weight * 0.7; // steer left
                } else {
                    // Direct forward obstacle: turn whichever way is clearer
                    steerAvoidanceForce += (rayDistances[1] > rayDistances[2] ? -0.5 : 0.5);
                }
            }
        }

        car.sensorRays = rayDistances;

        // Blend waypoint following with obstacle avoidance
        double totalSteerDemand = (angleDiff * 0.7) + (steerAvoidanceForce * 0.9);

        car.turnLeft = totalSteerDemand < -0.06;
        car.turnRight = totalSteerDemand > 0.06;

        // Dynamic speed control: brake if turn is sharp or obstacle is close ahead
        boolean obstacleAhead = rayDistances[0] < 55.0;
        if (Math.abs(angleDiff) > 0.6 || obstacleAhead) {
            car.accelerate = car.speed > (car.maxSpeed * 0.45) ? false : true;
            car.brake = car.speed > (car.maxSpeed * 0.6);
        } else {
            car.accelerate = true;
            car.brake = false;
        }
    }

    /**
     * Casts a ray from (rx, ry) in direction `angle` to detect track borders and other cars.
     */
    private double castRay(double rx, double ry, double angle, double maxDist, Car self) {
        double step = 8.0;
        double currentDist = 0.0;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        while (currentDist < maxDist) {
            currentDist += step;
            double px = rx + cos * currentDist;
            double py = ry + sin * currentDist;

            // Check if outside track boundary
            if (getDistanceToTrackCenter(px, py) > (TRACK_ROAD_WIDTH / 2.0 - 10)) {
                return currentDist;
            }

            // Check if hitting another car
            for (Car other : allCars) {
                if (other != self && Math.hypot(other.x - px, other.y - py) < 22) {
                    return currentDist;
                }
            }
        }
        return maxDist;
    }

    /**
     * Calculates distance of point (px, py) to the nearest segment of the track centerline.
     */
    private double getDistanceToTrackCenter(double px, double py) {
        double minDistance = Double.MAX_VALUE;
        int n = WAYPOINTS.length;
        for (int i = 0; i < n; i++) {
            Point2D.Double a = WAYPOINTS[i];
            Point2D.Double b = WAYPOINTS[(i + 1) % n];
            double dist = ptSegDist(a.x, a.y, b.x, b.y, px, py);
            if (dist < minDistance) {
                minDistance = dist;
            }
        }
        return minDistance;
    }

    private double ptSegDist(double x1, double y1, double x2, double y2, double px, double py) {
        return Line2D.ptSegDist(x1, y1, x2, y2, px, py);
    }

    private double normalizeAngle(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    private void resolveCarCollision(Car c1, Car c2) {
        double dx = c2.x - c1.x;
        double dy = c2.y - c1.y;
        double dist = Math.hypot(dx, dy);
        double minDist = 30.0; // sum of radii

        if (dist < minDist && dist > 0.001) {
            double overlap = 0.5 * (minDist - dist);
            double nx = dx / dist;
            double ny = dy / dist;

            c1.x -= nx * overlap;
            c1.y -= ny * overlap;
            c2.x += nx * overlap;
            c2.y += ny * overlap;

            // Slight speed dampening on collision
            c1.speed *= 0.88;
            c2.speed *= 0.88;
        }
    }

    private void updateRankings() {
        allCars.sort((a, b) -> {
            if (a.lapCount != b.lapCount) {
                return Integer.compare(b.lapCount, a.lapCount);
            }
            if (a.targetWaypointIndex != b.targetWaypointIndex) {
                return Integer.compare(b.targetWaypointIndex, a.targetWaypointIndex);
            }
            // Proximity to target waypoint
            Point2D.Double wp = WAYPOINTS[a.targetWaypointIndex];
            double distA = Math.hypot(wp.x - a.x, wp.y - a.y);
            double distB = Math.hypot(wp.x - b.x, wp.y - b.y);
            return Double.compare(distA, distB);
        });

        for (int i = 0; i < allCars.size(); i++) {
            allCars.get(i).rank = i + 1;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Draw decorative ground details
        drawEnvironment(g2d);

        // 2. Draw Track Surface & Curbs
        drawTrack(g2d);

        // 3. Draw All Cars
        for (Car car : allCars) {
            car.draw(g2d, showSensors);
        }

        // 4. Draw HUD (Heads-Up Display)
        drawHUD(g2d);

        // 5. Draw Mini-Map
        drawMiniMap(g2d);

        // 6. Draw Game Over Overlay
        if (raceOver) {
            drawFinishScreen(g2d);
        }
    }

    private void drawEnvironment(Graphics2D g2d) {
        // Subtle grid pattern for texture
        g2d.setColor(new Color(30, 125, 30));
        for (int x = 0; x < SCREEN_WIDTH; x += 40) {
            g2d.drawLine(x, 0, x, SCREEN_HEIGHT);
        }
        for (int y = 0; y < SCREEN_HEIGHT; y += 40) {
            g2d.drawLine(0, y, SCREEN_WIDTH, y);
        }

        // Decorative tree patches
        int[][] trees = { {60, 80}, {1000, 80}, {500, 360}, {700, 320}, {60, 680}, {1020, 680} };
        for (int[] t : trees) {
            g2d.setColor(new Color(20, 80, 20));
            g2d.fillOval(t[0] - 18, t[1] - 18, 36, 36);
            g2d.setColor(new Color(26, 110, 26));
            g2d.fillOval(t[0] - 14, t[1] - 14, 28, 28);
        }
    }

    private void drawTrack(Graphics2D g2d) {
        Path2D.Double trackPath = new Path2D.Double();
        trackPath.moveTo(WAYPOINTS[0].x, WAYPOINTS[0].y);
        for (int i = 1; i < WAYPOINTS.length; i++) {
            trackPath.lineTo(WAYPOINTS[i].x, WAYPOINTS[i].y);
        }
        trackPath.closePath();

        // 1. Outer Curb (Red and White alternating border)
        g2d.setStroke(new BasicStroke((float) TRACK_ROAD_WIDTH + 18, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(new Color(190, 40, 40));
        g2d.draw(trackPath);

        float[] dashPattern = { 25f, 25f };
        g2d.setStroke(new BasicStroke((float) TRACK_ROAD_WIDTH + 18, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, dashPattern, 0f));
        g2d.setColor(Color.WHITE);
        g2d.draw(trackPath);

        // 2. Main Asphalt Road
        g2d.setStroke(new BasicStroke((float) TRACK_ROAD_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(new Color(45, 45, 48));
        g2d.draw(trackPath);

        // 3. Center Dashed Guide Line
        float[] centerDash = { 20f, 20f };
        g2d.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f, centerDash, 0f));
        g2d.setColor(new Color(230, 200, 50, 180)); // Warm yellow dashed
        g2d.draw(trackPath);

        // 4. Start / Finish Line
        Point2D.Double p0 = WAYPOINTS[0];
        Point2D.Double p1 = WAYPOINTS[1];
        double angle = Math.atan2(p1.y - p0.y, p1.x - p0.x);
        double perpAngle = angle + Math.PI / 2.0;

        AffineTransform oldTx = g2d.getTransform();
        g2d.translate(p0.x, p0.y);
        g2d.rotate(perpAngle);

        int checkWidth = 14;
        int checkHeight = 110;
        int sq = 11;
        for (int i = -checkHeight / 2; i < checkHeight / 2; i += sq) {
            for (int j = -checkWidth / 2; j < checkWidth / 2; j += sq) {
                boolean isWhite = ((i / sq + j / sq) % 2 == 0);
                g2d.setColor(isWhite ? Color.WHITE : Color.BLACK);
                g2d.fillRect(j, i, sq, sq);
            }
        }
        g2d.setTransform(oldTx);
    }

    private void drawHUD(Graphics2D g2d) {
        // Top HUD Dashboard Panel
        g2d.setColor(new Color(20, 24, 30, 210));
        g2d.fillRoundRect(20, 15, 420, 110, 16, 16);
        g2d.setColor(new Color(80, 120, 180));
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawRoundRect(20, 15, 420, 110, 16, 16);

        // Player Position Display (e.g. 1st, 2nd...)
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 26));
        String posSuffix = switch (playerCar.rank) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        g2d.drawString("POS: " + playerCar.rank + posSuffix, 40, 50);

        // Lap Counter
        g2d.setFont(new Font("SansSerif", Font.BOLD, 18));
        g2d.setColor(new Color(255, 215, 0));
        g2d.drawString("LAP: " + Math.min(playerCar.lapCount, TOTAL_LAPS) + " / " + TOTAL_LAPS, 220, 48);

        // Speedometer bar & text
        int speedKmh = (int) (playerCar.speed * 26);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g2d.setColor(Color.WHITE);
        g2d.drawString("Speed: " + speedKmh + " km/h", 40, 80);

        // Speed Bar
        g2d.setColor(new Color(60, 60, 60));
        g2d.fillRect(170, 69, 140, 12);
        float speedRatio = (float) Math.min(1.0, playerCar.speed / playerCar.maxSpeed);
        g2d.setColor(speedRatio > 0.8 ? Color.RED : (speedRatio > 0.4 ? Color.ORANGE : Color.GREEN));
        g2d.fillRect(170, 69, (int) (140 * speedRatio), 12);

        // Timer & Mode Info
        long totalSeconds = raceTimeElapsed / 1000;
        long millis = (raceTimeElapsed % 1000) / 10;
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setFont(new Font("Monospaced", Font.BOLD, 15));
        g2d.drawString(String.format("TIME: %02d:%02d.%02d", totalSeconds / 60, totalSeconds % 60, millis), 40, 110);

        // Autopilot status
        if (playerCar.isAI) {
            g2d.setColor(new Color(50, 255, 120));
            g2d.drawString("[AI AUTOPILOT ON]", 230, 110);
        } else {
            g2d.setColor(new Color(150, 200, 255));
            g2d.drawString("[MANUAL MODE]", 230, 110);
        }

        // Controls hint in bottom-left
        g2d.setColor(new Color(0, 0, 0, 170));
        g2d.fillRoundRect(20, SCREEN_HEIGHT - 65, 480, 45, 10, 10);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g2d.drawString("Controls: [Arrow Keys / WASD] Drive | [A] Autopilot | [S] Sensors | [R] Restart", 30, SCREEN_HEIGHT - 38);
    }

    private void drawMiniMap(Graphics2D g2d) {
        int mapW = 160;
        int mapH = 120;
        int mapX = SCREEN_WIDTH - mapW - 25;
        int mapY = 20;

        g2d.setColor(new Color(15, 15, 20, 200));
        g2d.fillRoundRect(mapX, mapY, mapW, mapH, 12, 12);
        g2d.setColor(new Color(100, 100, 100));
        g2d.drawRoundRect(mapX, mapY, mapW, mapH, 12, 12);

        double scaleX = (double) (mapW - 30) / SCREEN_WIDTH;
        double scaleY = (double) (mapH - 30) / SCREEN_HEIGHT;

        // Draw track outline on mini map
        g2d.setColor(new Color(90, 90, 90));
        g2d.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double miniPath = new Path2D.Double();
        miniPath.moveTo(mapX + 15 + WAYPOINTS[0].x * scaleX, mapY + 15 + WAYPOINTS[0].y * scaleY);
        for (int i = 1; i < WAYPOINTS.length; i++) {
            miniPath.lineTo(mapX + 15 + WAYPOINTS[i].x * scaleX, mapY + 15 + WAYPOINTS[i].y * scaleY);
        }
        miniPath.closePath();
        g2d.draw(miniPath);

        // Draw car blips on minimap
        for (Car car : allCars) {
            int cx = (int) (mapX + 15 + car.x * scaleX);
            int cy = (int) (mapY + 15 + car.y * scaleY);
            g2d.setColor(car.color);
            int size = (car == playerCar) ? 8 : 6;
            g2d.fillOval(cx - size / 2, cy - size / 2, size, size);
        }
    }

    private void drawFinishScreen(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 190));
        g2d.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 46));
        String title = playerCar.rank == 1 ? "VICTORY! YOU WON!" : "RACE FINISHED!";
        int tw = g2d.getFontMetrics().stringWidth(title);
        g2d.drawString(title, (SCREEN_WIDTH - tw) / 2, 180);

        // Leaderboard table
        g2d.setFont(new Font("SansSerif", Font.BOLD, 22));
        g2d.setColor(new Color(255, 215, 0));
        g2d.drawString("FINAL STANDINGS", SCREEN_WIDTH / 2 - 100, 240);

        g2d.setFont(new Font("Monospaced", Font.PLAIN, 19));
        int yOffset = 290;
        for (int i = 0; i < allCars.size(); i++) {
            Car c = allCars.get(i);
            long sec = c.finishTime / 1000;
            long ms = (c.finishTime % 1000) / 10;
            String timeStr = c.hasFinished ? String.format("%02d:%02d.%02d", sec / 60, sec % 60, ms) : "+1 Lap";
            String line = String.format("%d. %-15s - %s", (i + 1), c.name, timeStr);

            g2d.setColor(c == playerCar ? new Color(0, 220, 255) : Color.WHITE);
            g2d.drawString(line, SCREEN_WIDTH / 2 - 170, yOffset);
            yOffset += 40;
        }

        g2d.setFont(new Font("SansSerif", Font.BOLD, 20));
        g2d.setColor(new Color(255, 230, 100));
        String restartPrompt = "Press [R] to Race Again!";
        int rw = g2d.getFontMetrics().stringWidth(restartPrompt);
        g2d.drawString(restartPrompt, (SCREEN_WIDTH - rw) / 2, yOffset + 40);
    }

    // KeyListener implementation
    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_UP || code == KeyEvent.VK_W) keyUp = true;
        if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) keyDown = true;
        if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_A) keyLeft = true;
        if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_D) keyRight = true;

        if (code == KeyEvent.VK_P) {
            // Toggle AI autopilot for player car
            playerCar.isAI = !playerCar.isAI;
        }
        if (code == KeyEvent.VK_X || (code == KeyEvent.VK_S && (e.isControlDown() || e.isAltDown()))) {
            showSensors = !showSensors;
        }
        if (code == KeyEvent.VK_R) {
            initRace();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_UP || code == KeyEvent.VK_W) keyUp = false;
        if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) keyDown = false;
        if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_A) keyLeft = false;
        if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_D) keyRight = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Toggle sensors with lowercase/uppercase 's' when not driving with WASD
        if (e.getKeyChar() == 's' || e.getKeyChar() == 'S') {
            if (playerCar.isAI) {
                showSensors = !showSensors;
            }
        }
        if (e.getKeyChar() == 'a' || e.getKeyChar() == 'A') {
            // Can toggle AI Autopilot via single character keypress
            playerCar.isAI = !playerCar.isAI;
        }
    }

    /**
     * Represents a single vehicle in the race (Human or AI).
     */
    static class Car {
        String name;
        double x, y;
        double angle;
        double speed = 0.0;
        double maxSpeed = 7.2;
        double accelerationRate = 0.16;
        double friction = 0.965;
        double steerSpeed = 0.054;
        Color color;
        boolean isAI;

        // Controls
        boolean accelerate, brake, turnLeft, turnRight;

        // Navigation & Progress
        int targetWaypointIndex = 1;
        int lapCount = 1;
        int rank = 1;
        boolean hasFinished = false;
        long finishTime = 0;

        // Sensor readings for debugging / visual rendering
        double[] sensorRays = new double[5];

        public Car(String name, double x, double y, double angle, Color color, boolean isAI) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.color = color;
            this.isAI = isAI;
        }

        public void updatePhysics() {
            if (accelerate) {
                speed = Math.min(maxSpeed, speed + accelerationRate);
            }
            if (brake) {
                speed = Math.max(-2.5, speed - accelerationRate * 1.5);
            }

            // Natural road rolling resistance
            speed *= friction;
            if (Math.abs(speed) < 0.03) speed = 0;

            // Steering depends on movement direction
            if (Math.abs(speed) > 0.1) {
                double speedFactor = Math.min(1.0, Math.abs(speed) / 3.0);
                if (turnLeft) angle -= steerSpeed * speedFactor * Math.signum(speed);
                if (turnRight) angle += steerSpeed * speedFactor * Math.signum(speed);
            }

            x += Math.cos(angle) * speed;
            y += Math.sin(angle) * speed;
        }

        public void updateProgress(Point2D.Double[] waypoints) {
            Point2D.Double target = waypoints[targetWaypointIndex];
            double dist = Math.hypot(target.x - x, target.y - y);

            // Reached checkpoint/waypoint
            if (dist < 75.0) {
                targetWaypointIndex++;
                if (targetWaypointIndex >= waypoints.length) {
                    targetWaypointIndex = 0;
                    lapCount++;
                }
            }
        }

        public void draw(Graphics2D g2d, boolean renderSensors) {
            AffineTransform oldTx = g2d.getTransform();
            g2d.translate(x, y);
            g2d.rotate(angle);

            // 1. Draw sensor rays if enabled and AI active
            if (renderSensors && isAI && sensorRays != null) {
                double[] rayAngles = new double[] { 0, -0.4, 0.4, -0.85, 0.85 };
                for (int i = 0; i < rayAngles.length && i < sensorRays.length; i++) {
                    double rAngle = rayAngles[i];
                    double dist = sensorRays[i];
                    int rayX = (int) (Math.cos(rAngle) * dist);
                    int rayY = (int) (Math.sin(rAngle) * dist);

                    g2d.setColor(dist < 70 ? new Color(255, 40, 40, 160) : new Color(50, 255, 100, 110));
                    g2d.setStroke(new BasicStroke(1.5f));
                    g2d.drawLine(14, 0, rayX, rayY);
                    g2d.fillOval(rayX - 3, rayY - 3, 6, 6);
                }
            }

            // 2. Draw Car Body Shadow
            g2d.setColor(new Color(0, 0, 0, 80));
            g2d.fillRoundRect(-14, -8, 30, 18, 6, 6);

            // 3. Draw Wheels (4 tires)
            g2d.setColor(new Color(25, 25, 25));
            g2d.fillRect(-12, -12, 7, 4); // Rear left
            g2d.fillRect(-12, 8, 7, 4);  // Rear right
            g2d.fillRect(6, -12, 7, 4);   // Front left
            g2d.fillRect(6, 8, 7, 4);    // Front right

            // 4. Car Main Chassis
            g2d.setColor(color);
            g2d.fillRoundRect(-15, -10, 30, 20, 8, 8);

            // 5. Racing stripes & roof windshield
            g2d.setColor(new Color(255, 255, 255, 180));
            g2d.fillRect(-15, -2, 30, 4);

            // Windshield
            g2d.setColor(new Color(30, 30, 40));
            g2d.fillRoundRect(-5, -7, 12, 14, 4, 4);

            // 6. Headlights & Taillights
            g2d.setColor(Color.YELLOW);
            g2d.fillRect(14, -8, 2, 4);
            g2d.fillRect(14, 4, 2, 4);

            g2d.setColor(Color.RED);
            g2d.fillRect(-16, -7, 2, 3);
            g2d.fillRect(-16, 4, 2, 3);

            g2d.setTransform(oldTx);

            // Draw Car Name tag above car
            g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2d.setColor(Color.WHITE);
            int nw = g2d.getFontMetrics().stringWidth(name);
            g2d.drawString(name, (int) x - nw / 2, (int) y - 18);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("AI Car Racing Game - Java 2D Circuit");
            AICarRacingGame gamePanel = new AICarRacingGame();

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(gamePanel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
        });
    }
}
