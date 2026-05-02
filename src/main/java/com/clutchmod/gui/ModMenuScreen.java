package com.clutchmod.gui;

import com.clutchmod.ClutchMod;
import com.clutchmod.modules.combat.SilentAimModule;
import com.clutchmod.modules.movement.ClutchModule;
import com.clutchmod.modules.placement.FastPlaceModule;
import com.clutchmod.modules.visual.PerspectiveModule;
import com.clutchmod.modules.visual.PlayerESPModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ModMenuScreen extends GuiScreen {

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final int C_BG      = color(13,  14,  17,  255);
    private static final int C_SURF    = color(18,  20,  26,  255);
    private static final int C_SURF2   = color(25,  28,  36,  255);
    private static final int C_BORDER  = color(38,  42,  52,  255);
    private static final int C_TEXT    = color(236, 240, 244, 255);
    private static final int C_MUTED   = color(100, 108, 130, 255);
    private static final int C_ACCENT  = color(74,  144, 217, 255);
    private static final int C_GREEN   = color(76,  175, 110, 255);
    private static final int C_RED     = color(229, 83,  83,  255);
    private static final int C_YELLOW  = color(232, 168, 56,  255);
    private static final int C_OVERLAY = color(0,   0,   0,   160);

    // ── Layout ────────────────────────────────────────────────────────────────
    // BUG FIX: window was 550x460 — too tall for 768p screens and the tab row
    // overflowed horizontally. Reduced to 520x400 and tab widths recalculated
    // so all 5 tabs fit without truncation.
    private static final int PW = 520;
    private static final int PH = 400;
    // Tab geometry: 5 tabs, each 74px wide, 8px gap, starting at x+12
    private static final int TAB_W    = 74;
    private static final int TAB_GAP  = 6;
    private static final int TAB_Y_OFF = 34; // relative to py
    private static final int CONTENT_Y_OFF = 64; // relative to py — where content starts

    private int px, py;
    private int activeTab = 0;

    // ── Widget lists ──────────────────────────────────────────────────────────
    private final List<ToggleEntry>   toggles   = new ArrayList<ToggleEntry>();
    private final List<IntFieldEntry> intFields = new ArrayList<IntFieldEntry>();

    interface ToggleAction { void run(); }
    interface IntConsumer  { void accept(int value); }

    private static class ToggleEntry {
        final int id, x, y, w, h;
        final String label;
        final boolean on;
        final ToggleAction action;
        ToggleEntry(int id, int x, int y, int w, int h, String label, boolean on, ToggleAction action) {
            this.id = id; this.x = x; this.y = y; this.w = w; this.h = h;
            this.label = label; this.on = on; this.action = action;
        }
    }

    private static class IntFieldEntry {
        final GuiTextField field;
        final String label;
        final int lx, ly;
        final IntConsumer cb;
        IntFieldEntry(GuiTextField field, String label, int lx, int ly, IntConsumer cb) {
            this.field = field; this.label = label; this.lx = lx; this.ly = ly; this.cb = cb;
        }
    }

    private static int color(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ── Helpers to check whether a module is ready ────────────────────────────
    /** Show "Mod not initialized" label and return false if mod is null. */
    private boolean checkMod(Object mod) {
        if (mod != null) return true;
        int bx = px + 14;
        int y  = py + CONTENT_Y_OFF + 10;
        drawRect(bx, y, bx + 260, y + 28, C_SURF);
        drawRect(bx, y, bx + 3, y + 28, C_RED);
        // We can't draw text here (this is called from buildWidgets not drawScreen),
        // so add a disabled button as a label instead.
        GuiButton label = new GuiButton(998, bx, y + 4, 260, 20, "Mod not initialized — restart game") {
            @Override public void drawButton(Minecraft mc, int mx, int my) {
                drawRect(xPosition, yPosition, xPosition + width, yPosition + height, C_SURF);
                drawRect(xPosition, yPosition, xPosition + 3, yPosition + height, C_RED);
                fontRendererObj.drawStringWithShadow(displayString,
                        xPosition + 6, yPosition + 6, C_RED);
            }
        };
        label.enabled = false;
        buttonList.add(label);
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  INIT / BUILD
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initGui() {
        // Clamp window position so it fits any screen size
        px = Math.max(4, (width  - PW) / 2);
        py = Math.max(4, (height - PH) / 2);
        buttonList.clear();
        toggles.clear();
        intFields.clear();
        buildWidgets();
    }

    private void buildWidgets() {
        // ── Tab buttons ──────────────────────────────────────────────────────
        String[] tabNames = { "Clutch", "Silent Aim", "Fast Place", "Perspective", "PlayerESP" };
        for (int i = 0; i < tabNames.length; i++) {
            int tx = px + 12 + i * (TAB_W + TAB_GAP);
            buttonList.add(new DarkButton(i, tx, py + TAB_Y_OFF, TAB_W, 20, tabNames[i]));
        }
        // Close button
        buttonList.add(new DarkButton(99, px + PW - 24, py + 5, 18, 18, "x"));

        // ── Content ──────────────────────────────────────────────────────────
        if      (activeTab == 0) buildClutchTab();
        else if (activeTab == 1) buildSilentAimTab();
        else if (activeTab == 2) buildFastPlaceTab();
        else if (activeTab == 3) buildPerspectiveTab();
        else if (activeTab == 4) buildPlayerESPTab();
    }

    // ── Per-tab content builders ──────────────────────────────────────────────

    private void buildClutchTab() {
        final ClutchModule cm = ClutchMod.CLUTCH;
        if (!checkMod(cm)) return;

        int bx = px + 14;
        int y  = py + CONTENT_Y_OFF;

        addToggle(100, bx, y, 220, 22, "Clutch", cm.isEnabled(), new ToggleAction() {
            public void run() { cm.toggle(); rebuild(); }
        }); y += 30;

        // Activation conditions column
        y += 14; // section label gap
        addToggle(110, bx, y, 230, 18, "On void", cm.isActivateOnVoid(), new ToggleAction() {
            public void run() { cm.setActivateOnVoid(!cm.isActivateOnVoid()); rebuild(); }
        }); y += 22;
        addToggle(111, bx, y, 230, 18, "On lethal fall", cm.isActivateOnLethalFall(), new ToggleAction() {
            public void run() { cm.setActivateOnLethalFall(!cm.isActivateOnLethalFall()); rebuild(); }
        }); y += 22;
        addToggle(112, bx, y, 230, 18, "On more than X blocks", cm.isActivateOnMoreThanX(), new ToggleAction() {
            public void run() { cm.setActivateOnMoreThanX(!cm.isActivateOnMoreThanX()); rebuild(); }
        }); y += 22;
        addIntField(200, bx, y, 60, 16, "Blocks", cm.getTriggerBlocks(), new IntConsumer() {
            public void accept(int v) { cm.setTriggerBlocks(v); }
        }); y += 24;

        // Additional settings column
        y += 10;
        addToggle(120, bx, y, 230, 18, "Silent aim (see tab)", cm.isUseSilentAim(), new ToggleAction() {
            public void run() { cm.setUseSilentAim(!cm.isUseSilentAim()); rebuild(); }
        }); y += 22;
        addToggle(121, bx, y, 230, 18, "Show block count", cm.isShowBlockCount(), new ToggleAction() {
            public void run() { cm.setShowBlockCount(!cm.isShowBlockCount()); rebuild(); }
        }); y += 22;
        addToggle(122, bx, y, 230, 18, "Reset angle", cm.isResetAngle(), new ToggleAction() {
            public void run() { cm.setResetAngle(!cm.isResetAngle()); rebuild(); }
        }); y += 22;
        addToggle(123, bx, y, 230, 18, "Return to slot", cm.isReturnToSlot(), new ToggleAction() {
            public void run() { cm.setReturnToSlot(!cm.isReturnToSlot()); rebuild(); }
        }); y += 22;
        addToggle(124, bx, y, 230, 18, "Allow staircase up", cm.isAllowStaircaseUp(), new ToggleAction() {
            public void run() { cm.setAllowStaircaseUp(!cm.isAllowStaircaseUp()); rebuild(); }
        }); y += 22;
        addToggle(125, bx, y, 230, 18, "Clutch move delay", cm.isClutchMoveDelay(), new ToggleAction() {
            public void run() { cm.setClutchMoveDelay(!cm.isClutchMoveDelay()); rebuild(); }
        }); y += 22;
        addIntField(201, bx, y, 60, 16, "Max blocks (0=unlim)", cm.getMaxBlocks(), new IntConsumer() {
            public void accept(int v) { cm.setMaxBlocks(v); }
        });

        // Right column — block filtering
        int rx = px + 14 + 262;
        int ry = py + CONTENT_Y_OFF + 30 + 14;
        buttonList.add(new DarkButton(300, rx, ry, 180, 20,
                "Filter: " + cm.getFilterMode().name()));
    }

    private void buildSilentAimTab() {
        final SilentAimModule sa = ClutchMod.SILENT_AIM;
        if (!checkMod(sa)) return;

        int bx = px + 14;
        int y  = py + CONTENT_Y_OFF;

        addToggle(400, bx, y, 220, 22, "Silent Aim", sa.isEnabled(), new ToggleAction() {
            public void run() { sa.toggle(); rebuild(); }
        }); y += 30;

        y += 14;
        SilentAimModule.MovementMode[] modes = SilentAimModule.MovementMode.values();
        int mw = (PW - 30) / modes.length - 6;
        for (int i = 0; i < modes.length; i++) {
            boolean sel = sa.getMovementMode() == modes[i];
            buttonList.add(new DarkButton(500 + i, bx + i * (mw + 8), y, mw, 20,
                    (sel ? "\u25B6 " : "") + modes[i].name()));
        }
        y += 26;

        y += 10;
        addToggle(410, bx, y, 230, 18, "3rd Person Aim View", sa.isThirdPersonAimView(), new ToggleAction() {
            public void run() { sa.setThirdPersonAimView(!sa.isThirdPersonAimView()); rebuild(); }
        }); y += 22;
        addToggle(411, bx, y, 230, 18, "Aim Indicator", sa.isAimIndicator(), new ToggleAction() {
            public void run() { sa.setAimIndicator(!sa.isAimIndicator()); rebuild(); }
        }); y += 22;

        y += 10;
        addToggle(420, bx, y, 230, 18, "Use Reach", sa.isUseReach(), new ToggleAction() {
            public void run() { sa.setUseReach(!sa.isUseReach()); rebuild(); }
        }); y += 22;
        addToggle(421, bx, y, 230, 18, "Use Hitboxes", sa.isUseHitboxes(), new ToggleAction() {
            public void run() { sa.setUseHitboxes(!sa.isUseHitboxes()); rebuild(); }
        });
    }

    private void buildFastPlaceTab() {
        final FastPlaceModule fp = ClutchMod.FAST_PLACE;
        // BUG FIX: was missing null check — if init() never ran this NPEs
        if (!checkMod(fp)) return;

        int bx = px + 14;
        int y  = py + CONTENT_Y_OFF;

        addToggle(600, bx, y, 220, 22, "Fast Place", fp.isEnabled(), new ToggleAction() {
            public void run() { fp.toggle(); rebuild(); }
        }); y += 30;

        y += 14;
        addToggle(610, bx, y, 230, 18, "Block Placement", fp.isPlaceBlock(), new ToggleAction() {
            public void run() { fp.setPlaceBlock(!fp.isPlaceBlock()); rebuild(); }
        }); y += 22;
        addToggle(611, bx, y, 230, 18, "Block Breaking", fp.isBreakBlock(), new ToggleAction() {
            public void run() { fp.setBreakBlock(!fp.isBreakBlock()); rebuild(); }
        }); y += 22;

        y += 10;
        addIntField(650, bx, y, 60, 16, "Delay (ms)", fp.getDelayMs(), new IntConsumer() {
            public void accept(int v) { fp.setDelayMs(v); }
        });
    }

    private void buildPerspectiveTab() {
        final PerspectiveModule pm = ClutchMod.PERSPECTIVE;
        // BUG FIX: was missing null check
        if (!checkMod(pm)) return;

        int bx = px + 14;
        int y  = py + CONTENT_Y_OFF;

        addToggle(700, bx, y, 220, 22, "Perspective", pm.isEnabled(), new ToggleAction() {
            public void run() { pm.toggle(); rebuild(); }
        }); y += 30;

        y += 14;
        addToggle(710, bx, y, 230, 18, "Smooth Camera", pm.isSmoothCamera(), new ToggleAction() {
            public void run() { pm.setSmoothCamera(!pm.isSmoothCamera()); rebuild(); }
        }); y += 22;

        y += 10;
        addIntField(750, bx, y, 60, 16, "Distance", (int) pm.getCameraDistance(), new IntConsumer() {
            public void accept(int v) { pm.setCameraDistance(v); }
        }); y += 24;
        addIntField(751, bx, y, 60, 16, "Height offset", (int) pm.getHeightOffset(), new IntConsumer() {
            public void accept(int v) { pm.setHeightOffset(v); }
        }); y += 24;
        addIntField(752, bx, y, 60, 16, "Side offset", (int) pm.getSideOffset(), new IntConsumer() {
            public void accept(int v) { pm.setSideOffset(v); }
        });
    }

    private void buildPlayerESPTab() {
        final PlayerESPModule esp = ClutchMod.PLAYER_ESP;
        // BUG FIX: was missing null check — clicked PlayerESP tab when PLAYER_ESP
        // was null (init() hadn't run), causing NPE and game crash
        if (!checkMod(esp)) return;

        int bx = px + 14;
        int y  = py + CONTENT_Y_OFF;

        addToggle(800, bx, y, 220, 22, "PlayerESP", esp.isEnabled(), new ToggleAction() {
            public void run() { esp.toggle(); rebuild(); }
        }); y += 30;

        y += 14;
        addToggle(810, bx, y, 230, 18, "Show Health", esp.isShowHealth(), new ToggleAction() {
            public void run() { esp.setShowHealth(!esp.isShowHealth()); rebuild(); }
        }); y += 22;
        addToggle(811, bx, y, 230, 18, "Show Name", esp.isShowName(), new ToggleAction() {
            public void run() { esp.setShowName(!esp.isShowName()); rebuild(); }
        }); y += 22;
        addToggle(812, bx, y, 230, 18, "Show Background", esp.isShowBackground(), new ToggleAction() {
            public void run() { esp.setShowBackground(!esp.isShowBackground()); rebuild(); }
        }); y += 22;
        addToggle(813, bx, y, 230, 18, "Hide Bots", esp.isHideBots(), new ToggleAction() {
            public void run() { esp.setHideBots(!esp.isHideBots()); rebuild(); }
        }); y += 22;
        addToggle(814, bx, y, 230, 18, "Hide Invisibles", esp.isHideInvisibles(), new ToggleAction() {
            public void run() { esp.setHideInvisibles(!esp.isHideInvisibles()); rebuild(); }
        });
    }

    // ── Widget factories ─────────────────────────────────────────────────────

    private void addToggle(int id, int x, int y, int w, int h,
                           String label, boolean on, ToggleAction action) {
        toggles.add(new ToggleEntry(id, x, y, w, h, label, on, action));
        buttonList.add(new DarkButton(id, x, y, w, h,
                (on ? "\u25A0 " : "\u25A1 ") + label));
    }

    private void addIntField(int id, int x, int y, int w, int h,
                             String label, int current, IntConsumer cb) {
        int fieldX = x + fontRendererObj.getStringWidth(label + ": ") + 4;
        GuiTextField field = new GuiTextField(id, fontRendererObj, fieldX, y, w, h);
        field.setText(String.valueOf(current));
        intFields.add(new IntFieldEntry(field, label, x, y + (h - 8) / 2, cb));
    }

    private void rebuild() { initGui(); }

    // ─────────────────────────────────────────────────────────────────────────
    //  INPUT
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void actionPerformed(GuiButton btn) {
        if (btn.id >= 0 && btn.id <= 4) { activeTab = btn.id; rebuild(); return; }
        if (btn.id == 99)               { mc.displayGuiScreen(null); return; }

        // Filter mode toggle (Clutch tab)
        if (btn.id == 300 && ClutchMod.CLUTCH != null) {
            ClutchMod.CLUTCH.setFilterMode(
                    ClutchMod.CLUTCH.getFilterMode() == ClutchModule.FilterMode.BLACKLIST
                    ? ClutchModule.FilterMode.WHITELIST
                    : ClutchModule.FilterMode.BLACKLIST);
            rebuild(); return;
        }

        // Silent Aim movement mode buttons
        if (btn.id >= 500 && btn.id <= 502 && ClutchMod.SILENT_AIM != null) {
            ClutchMod.SILENT_AIM.setMovementMode(
                    SilentAimModule.MovementMode.values()[btn.id - 500]);
            rebuild(); return;
        }

        // Toggle buttons
        for (ToggleEntry te : toggles) {
            if (te.id == btn.id) { te.action.run(); return; }
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == 1) { mc.displayGuiScreen(null); return; } // ESC closes
        super.keyTyped(c, key);
        for (IntFieldEntry ife : intFields) {
            if (ife.field.isFocused()) {
                ife.field.textboxKeyTyped(c, key);
                try {
                    ife.cb.accept(Integer.parseInt(ife.field.getText().trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    @Override
    public void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        for (IntFieldEntry ife : intFields) ife.field.mouseClicked(mx, my, button);
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    // ─────────────────────────────────────────────────────────────────────────
    //  RENDERING
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void drawScreen(int mx, int my, float pt) {
        // Background overlay
        drawRect(0, 0, width, height, C_OVERLAY);

        // Panel background
        drawRect(px, py, px + PW, py + PH, C_BG);

        // Top accent bar
        drawGradientRect(px, py, px + PW, py + 3, C_ACCENT, color(91, 77, 217, 255));

        // Border
        drawHLine(px, px + PW - 1, py,          C_BORDER);
        drawHLine(px, px + PW - 1, py + PH - 1, C_BORDER);
        drawVLine(px,          py, py + PH - 1, C_BORDER);
        drawVLine(px + PW - 1, py, py + PH - 1, C_BORDER);

        // Header text
        fontRendererObj.drawStringWithShadow("ClutchMod", px + 12, py + 8, C_TEXT);
        fontRendererObj.drawStringWithShadow("v1.0  |  G to close", px + 86, py + 9, C_MUTED);

        // Active/inactive badge
        boolean masterOn = isCurrentTabEnabled();
        String badge = masterOn ? "\u25CF ACTIVE" : "\u25CB INACTIVE";
        int badgeW = fontRendererObj.getStringWidth(badge);
        fontRendererObj.drawStringWithShadow(badge, px + PW - badgeW - 28, py + 8,
                masterOn ? C_GREEN : C_RED);

        // Tab underline
        int tabLineY = py + TAB_Y_OFF + 20;
        drawRect(px + 12, tabLineY, px + PW - 12, tabLineY + 1, C_BORDER);
        int activeTX = px + 12 + activeTab * (TAB_W + TAB_GAP);
        drawRect(activeTX, tabLineY - 1, activeTX + TAB_W, tabLineY + 2, C_ACCENT);

        // Content section labels
        drawContentLabels();

        // Int field labels and boxes
        for (IntFieldEntry ife : intFields) {
            fontRendererObj.drawStringWithShadow(ife.label + ":", ife.lx, ife.ly, C_MUTED);
            ife.field.drawTextBox();
        }

        super.drawScreen(mx, my, pt);
    }

    private boolean isCurrentTabEnabled() {
        switch (activeTab) {
            case 0: return ClutchMod.CLUTCH      != null && ClutchMod.CLUTCH.isEnabled();
            case 1: return ClutchMod.SILENT_AIM  != null && ClutchMod.SILENT_AIM.isEnabled();
            case 2: return ClutchMod.FAST_PLACE  != null && ClutchMod.FAST_PLACE.isEnabled();
            case 3: return ClutchMod.PERSPECTIVE != null && ClutchMod.PERSPECTIVE.isEnabled();
            case 4: return ClutchMod.PLAYER_ESP  != null && ClutchMod.PLAYER_ESP.isEnabled();
            default: return false;
        }
    }

    private void drawContentLabels() {
        int bx = px + 14;
        int sectionY = py + CONTENT_Y_OFF + 30;
        switch (activeTab) {
            case 0:
                sectionLabel("Activation Conditions", bx, sectionY);
                sectionLabel("Additional Settings",   bx, sectionY + 14 + 3 * 22 + 24 + 10 - 4);
                if (ClutchMod.CLUTCH != null) {
                    int rx = px + 14 + 262;
                    sectionLabel("Block Selection", rx, sectionY);
                    drawInfoBox(rx, sectionY + 26, PW - 14 - 262 - 14, 50, C_ACCENT,
                            color(0, 25, 60, 100),
                            "Block Filtering",
                            "Blacklist: blocks can't be used.",
                            "Whitelist: only listed blocks used.");
                }
                break;
            case 1:
                sectionLabel("Movement Mode", bx, sectionY);
                int infoY = sectionY + 14 + 26 + 10;
                drawInfoBox(bx, infoY, PW - 28, 32, C_GREEN, color(0, 35, 15, 100),
                        "Anticheat",
                        "PROPER mode only is safe on AC servers.");
                sectionLabel("Features",         bx, infoY + 40);
                sectionLabel("Advanced Options",  bx, infoY + 40 + 14 + 2 * 22 + 10);
                drawInfoBox(bx, infoY + 40 + 14 + 2 * 22 + 10 + 14 + 2 * 22 + 10, PW - 28, 40,
                        C_YELLOW, color(45, 30, 0, 100),
                        "Warning",
                        "Reach and Hitboxes are detectable.",
                        "Silent Aim alone does NOT extend reach.");
                break;
            case 2: sectionLabel("Settings", bx, sectionY); break;
            case 3: sectionLabel("Camera Settings", bx, sectionY); break;
            case 4: sectionLabel("ESP Options", bx, sectionY); break;
        }
    }

    // ── Drawing helpers ────────────────────────────────────────────────────────

    private void sectionLabel(String text, int x, int y) {
        int w = fontRendererObj.getStringWidth(text) + 6;
        drawRect(x - 1, y - 1, x + w, y + 10, C_BG);
        fontRendererObj.drawStringWithShadow(text, x + 1, y, C_ACCENT);
    }

    private void drawInfoBox(int x, int y, int w, int h, int border, int bg, String... lines) {
        drawRect(x,     y,     x + w, y + h, bg);
        drawRect(x,     y,     x + 3, y + h, border);
        drawHLine(x,     x + w, y,         border);
        drawHLine(x,     x + w, y + h - 1, border);
        drawVLine(x + w, y,     y + h,      border);

        int ty = y + 4;
        for (int i = 0; i < lines.length; i++) {
            fontRendererObj.drawStringWithShadow(lines[i], x + 7, ty,
                    i == 0 ? border : C_MUTED);
            ty += 10;
        }
    }

    private void drawHLine(int x1, int x2, int y, int c) {
        drawRect(Math.min(x1, x2), y, Math.max(x1, x2) + 1, y + 1, c);
    }
    private void drawVLine(int x, int y1, int y2, int c) {
        drawRect(x, Math.min(y1, y2) + 1, x + 1, Math.max(y1, y2), c);
    }

    // ── Custom button renderer ─────────────────────────────────────────────────

    private class DarkButton extends GuiButton {
        DarkButton(int id, int x, int y, int w, int h, String label) {
            super(id, x, y, w, h, label);
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) return;
            boolean hovered = mx >= xPosition && mx < xPosition + width
                           && my >= yPosition && my < yPosition + height;

            boolean isTabActive = (id >= 0 && id <= 4 && id == activeTab);

            boolean isModeActive = false;
            if (id >= 500 && id <= 502 && ClutchMod.SILENT_AIM != null) {
                isModeActive = ClutchMod.SILENT_AIM.getMovementMode()
                        == SilentAimModule.MovementMode.values()[id - 500];
            }

            boolean isSelected = isTabActive || isModeActive;
            int bg = isSelected ? color(14, 28, 46, 255) : (hovered ? C_SURF2 : C_SURF);
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, bg);

            int bc = isSelected ? C_ACCENT : (hovered ? C_BORDER : color(28, 32, 40, 255));
            // 1px border
            drawRect(xPosition,             yPosition,              xPosition + width, yPosition + 1,      bc);
            drawRect(xPosition,             yPosition + height - 1, xPosition + width, yPosition + height, bc);
            drawRect(xPosition,             yPosition,              xPosition + 1,     yPosition + height, bc);
            drawRect(xPosition + width - 1, yPosition,              xPosition + width, yPosition + height, bc);

            // Text colour
            int tc = isSelected ? C_ACCENT : (hovered ? C_TEXT : C_MUTED);
            // Toggle buttons use green/grey based on state
            if (id >= 100 && id < 500 || id >= 600) {
                for (ToggleEntry te : toggles) {
                    if (te.id == id) { tc = te.on ? C_GREEN : C_MUTED; break; }
                }
            }
            if (id == 99) tc = C_RED; // Close button always red

            fontRendererObj.drawStringWithShadow(displayString,
                    xPosition + (width  - fontRendererObj.getStringWidth(displayString)) / 2,
                    yPosition + (height - 8) / 2,
                    tc);
        }
    }
}
