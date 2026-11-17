package net.shiroha233.roadweaver.client.map.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.shiroha233.roadweaver.client.map.cache.ClientMapNotes;
import net.shiroha233.roadweaver.client.ui.RoadWeaverScreen;
import net.shiroha233.roadweaver.client.ui.ScreenBackgrounds;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图笔记编辑界面 - 采用类似原版书与笔的风格
 */
public class NoteEditScreen extends RoadWeaverScreen {
    private static final Identifier BOOK_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/book.png");
    
    private static final int BOOK_WIDTH = 192;
    private static final int BOOK_HEIGHT = 192;
    private static final int TEXT_WIDTH = 114;
    private static final int TEXT_X_OFFSET = 36;
    private static final int TEXT_Y_OFFSET = 32;
    private static final int MAX_LINES = 14;
    private static final int LINE_HEIGHT = 9;
    
    private final BlockPos targetPos;
    private final Screen parent;
    private final List<String> lines = new ArrayList<>();
    
    private int cursorLine = 0;
    private int cursorPos = 0;
    private int bookX, bookY;
    private long lastBlink = 0;
    private boolean cursorVisible = true;

    public NoteEditScreen(BlockPos targetPos, Screen parent) {
        super(Component.translatable("gui.roadweaver.map.note.title"));
        this.targetPos = targetPos;
        this.parent = parent;
        
        List<String> existing = ClientMapNotes.getNotes(targetPos);
        if (!existing.isEmpty()) {
            lines.addAll(existing);
        } else {
            lines.add("");
        }
    }

    @Override
    protected void init() {
        bookX = (this.width - BOOK_WIDTH) / 2;
        bookY = (this.height - BOOK_HEIGHT) / 2;
        
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.roadweaver.common.save"),
                b -> save()
        ).bounds(bookX + BOOK_WIDTH / 2 - 50, bookY + BOOK_HEIGHT + 4, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                     float partialTick) {
        ScreenBackgrounds.render(g, this.width, this.height);
        
        // 书本纹理为 256x256，界面只取左上角 192x192 的区域。
        g.blit(BOOK_TEXTURE, bookX, bookY, BOOK_WIDTH, BOOK_HEIGHT,
                0.0F, BOOK_WIDTH / 256.0F, 0.0F, BOOK_HEIGHT / 256.0F);
        
        String alias = ClientMapNotes.getAlias(targetPos);
        String titleStr = (alias != null && !alias.isEmpty()) 
                ? alias 
                : String.format("(%d, %d)", targetPos.getX(), targetPos.getZ());
        g.text(this.font, titleStr, bookX + TEXT_X_OFFSET, bookY + 18, 0x000000, false);
        
        int textX = bookX + TEXT_X_OFFSET;
        int textY = bookY + TEXT_Y_OFFSET;
        
        for (int i = 0; i < Math.min(lines.size(), MAX_LINES); i++) {
            String line = lines.get(i);
            g.text(this.font, line, textX, textY + i * LINE_HEIGHT, 0x000000, false);
        }
        
        updateCursorBlink();
        if (cursorVisible && cursorLine < lines.size()) {
            String currentLine = lines.get(cursorLine);
            int cursorX = textX + this.font.width(currentLine.substring(0, Math.min(cursorPos, currentLine.length())));
            int cursorY = textY + cursorLine * LINE_HEIGHT;
            g.fill(cursorX, cursorY, cursorX + 1, cursorY + LINE_HEIGHT, 0xFF000000);
        }
        
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }
    
    private void updateCursorBlink() {
        long now = System.currentTimeMillis();
        if (now - lastBlink > 500) {
            cursorVisible = !cursorVisible;
            lastBlink = now;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.keycode();
        int modifiers = event.modifiers();

        if (keyCode == 256) {
            this.minecraft.gui.setScreen(parent);
            return true;
        }
        
        if (keyCode == 257 || keyCode == 335) {
            if (lines.size() < MAX_LINES) {
                String current = lines.get(cursorLine);
                String before = current.substring(0, Math.min(cursorPos, current.length()));
                String after = current.substring(Math.min(cursorPos, current.length()));
                lines.set(cursorLine, before);
                lines.add(cursorLine + 1, after);
                cursorLine++;
                cursorPos = 0;
            }
            return true;
        }
        
        if (keyCode == 259) {
            if (cursorPos > 0) {
                String current = lines.get(cursorLine);
                String newLine = current.substring(0, cursorPos - 1) + current.substring(cursorPos);
                lines.set(cursorLine, newLine);
                cursorPos--;
            } else if (cursorLine > 0) {
                String current = lines.remove(cursorLine);
                cursorLine--;
                String prev = lines.get(cursorLine);
                cursorPos = prev.length();
                lines.set(cursorLine, prev + current);
            }
            return true;
        }
        
        if (keyCode == 261) {
            String current = lines.get(cursorLine);
            if (cursorPos < current.length()) {
                String newLine = current.substring(0, cursorPos) + current.substring(cursorPos + 1);
                lines.set(cursorLine, newLine);
            } else if (cursorLine < lines.size() - 1) {
                String next = lines.remove(cursorLine + 1);
                lines.set(cursorLine, current + next);
            }
            return true;
        }
        
        if (keyCode == 263) {
            if (cursorPos > 0) cursorPos--;
            else if (cursorLine > 0) {
                cursorLine--;
                cursorPos = lines.get(cursorLine).length();
            }
            return true;
        }
        if (keyCode == 262) {
            String current = lines.get(cursorLine);
            if (cursorPos < current.length()) cursorPos++;
            else if (cursorLine < lines.size() - 1) {
                cursorLine++;
                cursorPos = 0;
            }
            return true;
        }
        if (keyCode == 265) {
            if (cursorLine > 0) {
                cursorLine--;
                cursorPos = Math.min(cursorPos, lines.get(cursorLine).length());
            }
            return true;
        }
        if (keyCode == 264) {
            if (cursorLine < lines.size() - 1) {
                cursorLine++;
                cursorPos = Math.min(cursorPos, lines.get(cursorLine).length());
            }
            return true;
        }
        
        if (keyCode == 268) {
            cursorPos = 0;
            return true;
        }
        if (keyCode == 269) {
            cursorPos = lines.get(cursorLine).length();
            return true;
        }
        
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char c = (char) event.codepoint();

        if (Character.isISOControl(c)) return false;
        
        String current = lines.get(cursorLine);
        String newLine = current.substring(0, cursorPos) + c + current.substring(cursorPos);
        if (this.font.width(newLine) <= TEXT_WIDTH) {
            lines.set(cursorLine, newLine);
            cursorPos++;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        int textX = bookX + TEXT_X_OFFSET;
        int textY = bookY + TEXT_Y_OFFSET;
        
        if (mouseX >= textX && mouseX < textX + TEXT_WIDTH 
            && mouseY >= textY && mouseY < textY + MAX_LINES * LINE_HEIGHT) {
            
            int clickedLine = (int) ((mouseY - textY) / LINE_HEIGHT);
            if (clickedLine < lines.size()) {
                cursorLine = clickedLine;
                String line = lines.get(cursorLine);
                int relX = (int) (mouseX - textX);
                cursorPos = 0;
                for (int i = 0; i <= line.length(); i++) {
                    if (this.font.width(line.substring(0, i)) >= relX) {
                        cursorPos = Math.max(0, i - 1);
                        break;
                    }
                    cursorPos = i;
                }
            }
            return true;
        }
        
        return super.mouseClicked(event, doubleClick);
    }

    private void save() {
        ClientMapNotes.clearNotes(targetPos);
        for (String line : lines) {
            if (!line.isEmpty()) {
                ClientMapNotes.addNote(targetPos, line);
            }
        }
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
