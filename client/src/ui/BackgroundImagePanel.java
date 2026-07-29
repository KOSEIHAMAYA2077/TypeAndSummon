package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

class BackgroundImagePanel extends JPanel {
    private final Image backgroundImage;
    private final boolean preserveAspectRatio;
    private BufferedImage cachedFrame;
    private int cachedFrameWidth;
    private int cachedFrameHeight;

    BackgroundImagePanel(String imagePath) {
        this(imagePath, false);
    }

    BackgroundImagePanel(String imagePath, boolean preserveAspectRatio) {
        this.preserveAspectRatio = preserveAspectRatio;
        setDoubleBuffered(true);
        File file = new File(imagePath);
        if (file.exists()) {
            backgroundImage = new ImageIcon(imagePath).getImage();
        } else {
            BufferedImage fallback = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = fallback.createGraphics();
            g2d.setColor(new Color(25, 25, 35));
            g2d.fillRect(0, 0, 1, 1);
            g2d.dispose();
            backgroundImage = fallback;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (cachedFrame == null || cachedFrameWidth != width || cachedFrameHeight != height) {
            rebuildCachedFrame(width, height);
        }
        if (cachedFrame != null) {
            g.drawImage(cachedFrame, 0, 0, null);
        }
    }

    private void rebuildCachedFrame(int width, int height) {
        cachedFrameWidth = width;
        cachedFrameHeight = height;
        cachedFrame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = cachedFrame.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (!preserveAspectRatio) {
                g2.drawImage(backgroundImage, 0, 0, width, height, this);
                return;
            }

            int imageWidth = backgroundImage.getWidth(this);
            int imageHeight = backgroundImage.getHeight(this);
            if (imageWidth <= 0 || imageHeight <= 0) {
                g2.setColor(new Color(25, 25, 35));
                g2.fillRect(0, 0, width, height);
                return;
            }

            double scale = Math.max((double) width / imageWidth, (double) height / imageHeight);
            int drawWidth = (int) Math.round(imageWidth * scale);
            int drawHeight = (int) Math.round(imageHeight * scale);
            int x = (width - drawWidth) / 2;
            int y = (height - drawHeight) / 2;
            g2.setColor(new Color(25, 25, 35));
            g2.fillRect(0, 0, width, height);
            g2.drawImage(backgroundImage, x, y, drawWidth, drawHeight, this);
        } finally {
            g2.dispose();
        }
    }
}
