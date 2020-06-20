/*
 * Advancements Enlarger by shedaniel.
 * Licensed under the CC-BY-NC-4.0.
 */

package me.shedaniel.advancementsenlarger.gui;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.network.ClientAdvancementManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.NarratorManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.c2s.play.AdvancementTabC2SPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.Lazy;

public class BiggerAdvancementsScreen extends Screen implements ClientAdvancementManager.Listener {
    private static final Identifier WINDOW_TEXTURE = new Identifier("advancements-enlarger:textures/gui/advancements/recipecontainer.png");
    private static final Identifier WINDOW_DARK_TEXTURE = new Identifier("advancements-enlarger:textures/gui/advancements/recipecontainer_dark.png");
    private static final Identifier TABS_TEXTURE = new Identifier("textures/gui/advancements/tabs.png");
    private static final Identifier TABS_DARK_TEXTURE = new Identifier("advancements-enlarger:textures/gui/advancements/tabs_dark.png");
    private final ClientAdvancementManager advancementHandler;
    private final Map<Advancement, BiggerAdvancementTab> tabs = Maps.newLinkedHashMap();
    private BiggerAdvancementTab selectedTab;
    private AdvancementsScreen screen;
    private boolean movingTab;
    private Lazy<Boolean> reiExists = new Lazy<>(() -> FabricLoader.getInstance().isModLoaded("roughlyenoughitems"));
    private Lazy<Method> darkModeMethod = new Lazy<>(() -> {
        if (!reiExists.get())
            return null;
        try {
            return Class.forName("me.shedaniel.rei.impl.ScreenHelper").getDeclaredMethod("isDarkModeEnabled");
        } catch (Throwable e) {
        }
        return null;
    });
    private BiggerAdvancementWidget underMouse;
    private List<BiggerAdvancementCriterionInfo> infoLines;
    
    public BiggerAdvancementsScreen(ClientAdvancementManager clientAdvancementManager, AdvancementsScreen screen) {
        super(NarratorManager.EMPTY);
        this.advancementHandler = clientAdvancementManager;
        this.screen = screen;
    }
    
    private boolean isDarkMode() {
        try {
            return darkModeMethod.get() != null && (boolean) darkModeMethod.get().invoke(null);
        } catch (Throwable e) {
            return false;
        }
    }
    
    protected void init() {
        this.tabs.clear();
        this.selectedTab = null;
        this.advancementHandler.setListener(this);
        if (this.selectedTab == null && !this.tabs.isEmpty()) {
            this.advancementHandler.selectTab((this.tabs.values().iterator().next()).getRoot(), true);
        } else {
            this.advancementHandler.selectTab(this.selectedTab == null ? null : this.selectedTab.getRoot(), true);
        }
        
    }
    
    public void removed() {
        this.advancementHandler.setListener((ClientAdvancementManager.Listener) null);
        ClientPlayNetworkHandler clientPlayNetworkHandler = this.client.getNetworkHandler();
        if (clientPlayNetworkHandler != null) {
            clientPlayNetworkHandler.sendPacket(AdvancementTabC2SPacket.close());
        }
        
    }
    
    @Override
    public boolean mouseScrolled(double d, double e, double amount) {
        if (selectedTab == null)
            return false;
        selectedTab.scroll(amount);
        return true;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            
            if (underMouse != null) {
                infoLines = underMouse.getCriteriaList();
                underMouse.dump();
            }

            int i = 8;
            int j = 33;
            Iterator var8 = this.tabs.values().iterator();
            
            while (var8.hasNext()) {
                BiggerAdvancementTab advancementTab = (BiggerAdvancementTab) var8.next();
                if (advancementTab.isClickOnTab(i, j, mouseX, mouseY)) {
                    this.advancementHandler.selectTab(advancementTab.getRoot(), true);
                    this.infoLines = null;
                    break;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.client.options.keyAdvancements.matchesKey(keyCode, scanCode)) {
            this.client.openScreen((Screen) null);
            this.client.mouse.lockCursor();
            return true;
        } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
    
    @Override
    public void render(MatrixStack stack, int mouseX, int mouseY, float delta) {
        int i = 8;
        int j = 33;
        this.renderBackground(stack);
        this.drawAdvancementTree(stack, mouseX, mouseY, i, j);
        this.drawWidgets(stack, i, j);
        this.drawWidgetTooltip(stack, mouseX, mouseY, i, j);
        
        if (infoLines != null) {
            RenderSystem.pushMatrix();
            // RenderSystem.disableDepthTest();
            RenderSystem.translatef(0.0F, 0.0F, 999.0F);
            i = width * 2 / 3;
            j = 55;
            for (BiggerAdvancementCriterionInfo line: infoLines) {
                this.textRenderer.draw(stack, line.getName(), i, j, line.getObtained() ? 0x00ff00 : 0xff0000);
                j += this.textRenderer.fontHeight+2;
            }
            RenderSystem.popMatrix();
        }
    }
    
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != 0) {
            this.movingTab = false;
            return false;
        } else {
            if (!this.movingTab) {
                this.movingTab = true;
            } else if (this.selectedTab != null) {
                this.selectedTab.move(deltaX, deltaY);
            }
            
            return true;
        }
    }
    
    private void drawAdvancementTree(MatrixStack stack, int mouseX, int mouseY, int x, int i) {
        BiggerAdvancementTab advancementTab = this.selectedTab;
        if (advancementTab == null) {
            fill(stack, x + 9, i + 18, width - 9, height - 17, -16777216);
            String string = I18n.translate("advancements.empty");
            int j = this.textRenderer.getWidth(string);
            textRenderer.draw(stack, string, (width - j) / 2, (height - 33) / 2 + 33 - 9 / 2, -1);
            textRenderer.draw(stack, ":(", (width - this.textRenderer.getWidth(":(")) / 2, (height - 33) / 2 + 33 + 9 + 9 / 2, -1);
        } else {
            RenderSystem.pushMatrix();
            RenderSystem.translatef((float) (x + 9), (float) (i + 18), 0.0F);
            advancementTab.render(stack);
            RenderSystem.popMatrix();
            RenderSystem.depthFunc(515);
            RenderSystem.disableDepthTest();
        }
    }
    
    public void drawWidgets(MatrixStack stack, int x, int i) {
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        drawWindow(stack, x, i);
        if (this.tabs.size() > 1) {
            this.client.getTextureManager().bindTexture(isDarkMode() ? TABS_DARK_TEXTURE : TABS_TEXTURE);
            Iterator var3 = this.tabs.values().iterator();
            
            BiggerAdvancementTab advancementTab2;
            while (var3.hasNext()) {
                advancementTab2 = (BiggerAdvancementTab) var3.next();
                advancementTab2.drawBackground(stack, x, i, advancementTab2 == this.selectedTab);
            }
            
            RenderSystem.enableRescaleNormal();
            RenderSystem.defaultBlendFunc();
            var3 = this.tabs.values().iterator();
            
            while (var3.hasNext()) {
                advancementTab2 = (BiggerAdvancementTab) var3.next();
                advancementTab2.drawIcon(stack, x, i, this.itemRenderer);
            }
            
            RenderSystem.disableBlend();
        }
        
        this.textRenderer.draw(stack, I18n.translate("gui.advancements"), (float) (x + 8), (float) (i + 6), isDarkMode() ? -1 : 4210752);
    }
    
    private void drawWindow(MatrixStack stack, int x, int y) {
        boolean darkMode = isDarkMode();
        this.client.getTextureManager().bindTexture(!darkMode ? WINDOW_TEXTURE : WINDOW_DARK_TEXTURE);
        int width = this.width - 16;
        int height = this.height - 41;
        //Four Corners
        this.drawTexture(stack, x, y, 106, 124 + 66, 4, 4);
        this.drawTexture(stack, x + width - 4, y, 252, 124 + 66, 4, 4);
        this.drawTexture(stack, x, y + height - 4, 106, 186 + 66, 4, 4);
        this.drawTexture(stack, x + width - 4, y + height - 4, 252, 186 + 66, 4, 4);
        
        //Sides
        for(int xx = 4; xx < width - 4; xx += 128) {
            int thisWidth = Math.min(128, width - 4 - xx);
            this.drawTexture(stack, x + xx, y, 110, 124 + 66, thisWidth, 4);
            this.drawTexture(stack, x + xx, y + height - 4, 110, 186 + 66, thisWidth, 4);
        }
        for(int yy = 4; yy < height - 4; yy += 50) {
            int thisHeight = Math.min(50, height - 4 - yy);
            this.drawTexture(stack, x, y + yy, 106, 128 + 66, 4, thisHeight);
            this.drawTexture(stack, x + width - 4, y + yy, 252, 128 + 66, 4, thisHeight);
        }
        int color = darkMode ? -13750738 : -3750202;
        fillGradient(stack, x + 4, y + 4, x + width - 4, y + 18, color, color);
        fillGradient(stack, x + 4, y + 4, x + 9, y + height - 4, color, color);
        fillGradient(stack, x + width - 9, y + 4, x + width - 4, y + height - 4, color, color);
        fillGradient(stack, x + 4, y + height - 9, x + width - 4, y + height - 4, color, color);
        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.disableAlphaTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.shadeModel(7425);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        bufferBuilder.begin(7, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(x + width - 9, y + 18, getBlitOffset()).color(0, 0, 0, 150).next();
        bufferBuilder.vertex(x + 9, y + 18, getBlitOffset()).color(0, 0, 0, 150).next();
        bufferBuilder.vertex(x + 9, y + 24, getBlitOffset()).color(0, 0, 0, 0).next();
        bufferBuilder.vertex(x + width - 9, y + 24, getBlitOffset()).color(0, 0, 0, 0).next();
        tessellator.draw();
        bufferBuilder.begin(7, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(x + width - 9, y + height - 9, getBlitOffset()).color(0, 0, 0, 150).next();
        bufferBuilder.vertex(x + 9, y + height - 9, getBlitOffset()).color(0, 0, 0, 150).next();
        bufferBuilder.vertex(x + 9, y + height - 15, getBlitOffset()).color(0, 0, 0, 0).next();
        bufferBuilder.vertex(x + width - 9, y + height - 15, getBlitOffset()).color(0, 0, 0, 0).next();
        tessellator.draw();
        bufferBuilder.begin(7, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(x + 15, y + 18, getBlitOffset()).color(0, 0, 0, 0).next();
        bufferBuilder.vertex(x + 9, y + 18, getBlitOffset()).color(0, 0, 0, 150).next();
        bufferBuilder.vertex(x + 9, y + height - 9, getBlitOffset()).color(0, 0, 0, 150).next();
        bufferBuilder.vertex(x + 15, y + height - 9, getBlitOffset()).color(0, 0, 0, 0).next();
        tessellator.draw();
        bufferBuilder.begin(7, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(x + width - 15, y + 18, getBlitOffset()).color(0, 0, 0, 0).next();
        bufferBuilder.vertex(x + width - 9, y + 18, getBlitOffset()).color(0, 0, 0, 150).next();
        bufferBuilder.vertex(x + width - 9, y + height - 9, getBlitOffset()).color(0, 0, 0, 150).next();
        bufferBuilder.vertex(x + width - 15, y + height - 9, getBlitOffset()).color(0, 0, 0, 0).next();
        tessellator.draw();
        RenderSystem.shadeModel(7424);
        RenderSystem.disableBlend();
        RenderSystem.enableAlphaTest();
        RenderSystem.enableTexture();
    }
    
    private int getBlitOffset() { return 0; }       // @TODO this was a method in 1.15.2 but isn't in 1.16
    
    private void drawWidgetTooltip(MatrixStack stack, int mouseX, int mouseY, int x, int y) {
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        if (this.selectedTab != null) {
            RenderSystem.pushMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.translatef((float) (x + 9), (float) (y + 18), 400.0F);
            underMouse = this.selectedTab.drawWidgetTooltip(stack, mouseX - x - 9, mouseY - y - 18, x, y);
            RenderSystem.disableDepthTest();
            RenderSystem.popMatrix();
        }
        
        if (this.tabs.size() > 1) {
            Iterator var5 = this.tabs.values().iterator();
            
            while (var5.hasNext()) {
                BiggerAdvancementTab advancementTab = (BiggerAdvancementTab) var5.next();
                if (advancementTab.isClickOnTab(x, y, (double) mouseX, (double) mouseY)) {
                    this.renderTooltip(stack, advancementTab.getTitle(), mouseX, mouseY);
                }
            }
        }
        
    }
    
    public void onRootAdded(Advancement root) {
        try {
            BiggerAdvancementTab advancementTab = BiggerAdvancementTab.create(this.client, this, this.tabs.size(), root);
            if (advancementTab != null) {
                this.tabs.put(root, advancementTab);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    
    public void onRootRemoved(Advancement root) {
    }
    
    public void onDependentAdded(Advancement dependent) {
        BiggerAdvancementTab advancementTab = this.getTab(dependent);
        if (advancementTab != null) {
            advancementTab.addAdvancement(dependent);
        }
        
    }
    
    public void onDependentRemoved(Advancement dependent) {
    }
    
    @Override
    public void setProgress(Advancement advancement, AdvancementProgress advancementProgress) {
        BiggerAdvancementWidget advancementWidget = this.getAdvancementWidget(advancement);
        if (advancementWidget != null) {
            advancementWidget.setProgress(advancementProgress);
        }
        
    }
    
    @Override
    public void selectTab(@Nullable Advancement advancement) {
        this.selectedTab = this.tabs.get(advancement);
    }
    
    public void onClear() {
        this.tabs.clear();
        this.selectedTab = null;
    }
    
    @Nullable
    public BiggerAdvancementWidget getAdvancementWidget(Advancement advancement) {
        BiggerAdvancementTab advancementTab = this.getTab(advancement);
        return advancementTab == null ? null : advancementTab.getWidget(advancement);
    }
    
    @Nullable
    private BiggerAdvancementTab getTab(Advancement advancement) {
        while (advancement.getParent() != null) {
            advancement = advancement.getParent();
        }
        
        return (BiggerAdvancementTab) this.tabs.get(advancement);
    }
}
