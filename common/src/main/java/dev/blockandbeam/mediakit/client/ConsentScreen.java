package dev.blockandbeam.mediakit.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import dev.blockandbeam.mediakit.api.Consent;
import dev.blockandbeam.mediakit.api.ExternalDependency;

/**
 * Dialog asking whether MediaKit may download and manage
 * external components. Lists each component with whether it is already
 * present; hovering a component shows where it was found.
 */
public final class ConsentScreen extends Screen {
    private static final int MARGIN = 50;
    private static final int LINE_HEIGHT = 9;

    private static final int CHECK_COLOR = 0xFF55FF55;
    private static final int MISS_COLOR = 0xFFFF5555;

    private static final ResourceLocation LINK_ICON =
            ResourceLocation.withDefaultNamespace("icon/link");

    private static final String INTRO =
            "MediaKit needs the following components to support all media features:";
    private static final String CLOSING =
            "Each is looked up on your system PATH first; anything missing is downloaded to the instance's mediakit/ folder.";

    private final List<ExternalDependency> dependencies;
    private final Consumer<Boolean> onAnswer;

    private List<Line> lines = List.of();
    private int titleTop;
    private int messageTop;

    public ConsentScreen(List<ExternalDependency> dependencies, Consumer<Boolean> onAnswer) {
        super(Component.literal("External Components"));
        this.dependencies = dependencies;
        this.onAnswer = onAnswer;
    }

    @Override
    protected void init() {
        super.init();
        lines = wrapBody();

        int messageHeight = lines.size() * LINE_HEIGHT;
        titleTop = Mth.clamp((height - messageHeight) / 2 - 29, 10, 80);
        messageTop = titleTop + 20;

        int buttonY = Math.max(messageTop + messageHeight + 20, height / 6 + 96);
        addRenderableWidget(Button.builder(Component.literal("Allow"), b -> accept())
                .bounds(width / 2 - 155, buttonY, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Decline"), b -> decline())
                .bounds(width / 2 + 5, buttonY, 150, 20).build());

        int y = messageTop;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line.dep() != null && isLastLineOfEntry(i)) {
                addLink(line, y);
            }
            y += LINE_HEIGHT;
        }
    }

    /** The last wrapped line of an entry, where its link icon goes. */
    private boolean isLastLineOfEntry(int index) {
        return index + 1 >= lines.size() || lines.get(index + 1).dep() != lines.get(index).dep();
    }

    private void addLink(Line line, int y) {
        ExternalDependency dep = line.dep();
        SpriteIconButton link = SpriteIconButton.builder(
                        Component.literal("Open " + dep.name() + " website"),
                        b -> openUrl(dep.url()), true)
                .size(15, 15)
                .sprite(LINK_ICON, 15, 15)
                .build();
        link.setPosition(width / 2 + font.width(line.text()) / 2 + 6, y - 3);
        link.setTooltip(Tooltip.create(Component.literal("Open: " + dep.url())));
        addRenderableWidget(link);
    }

    private static void openUrl(String url) {
        Util.getPlatform().openUri(URI.create(url));
    }

    private void accept() {
        Consent.accept();
        onAnswer.accept(true);
        Minecraft.getInstance().setScreen(null);
    }

    private void decline() {
        Consent.decline();
        onAnswer.accept(false);
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape, same as Decline.
            decline();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        onAnswer.accept(false);
        super.onClose();
    }

    private List<Line> wrapBody() {
        List<Line> wrapped = new ArrayList<>();
        addWrapped(wrapped, Component.literal(INTRO), null);
        wrapped.add(blank());
        for (ExternalDependency dep : dependencies) {
            MutableComponent entry = Component.empty();
            entry.append(Component.literal(dep.location() != null ? "\u2713 " : "\u2715 ")
                    .withColor(dep.location() != null ? CHECK_COLOR : MISS_COLOR));
            entry.append(Component.literal(dep.name() + ": " + dep.description()));
            addWrapped(wrapped, entry, dep);
            wrapped.add(blank());
        }
        addWrapped(wrapped, Component.literal(CLOSING), null);
        return wrapped;
    }

    private void addWrapped(List<Line> out, Component text, ExternalDependency dep) {
        for (FormattedCharSequence line : font.split(text, width - MARGIN * 2)) {
            out.add(new Line(line, dep));
        }
    }

    private static Line blank() {
        return new Line(FormattedCharSequence.EMPTY, null);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick); // background + widgets
        guiGraphics.drawCenteredString(font, title, width / 2, titleTop, 0xFFFFFF);
        int y = messageTop;
        for (Line line : lines) {
            guiGraphics.drawCenteredString(font, line.text(), width / 2, y, 0xFFFFFF);
            y += LINE_HEIGHT;
        }

        ExternalDependency hovered = hoveredDependency(mouseX, mouseY);
        if (hovered != null) {
            guiGraphics.renderTooltip(font, tooltip(hovered), mouseX, mouseY);
        }
    }

    private ExternalDependency hoveredDependency(int mouseX, int mouseY) {
        int y = messageTop;
        for (Line line : lines) {
            if (line.dep() != null && mouseY >= y && mouseY < y + LINE_HEIGHT) {
                int textWidth = font.width(line.text());
                if (mouseX >= width / 2 - textWidth / 2 - 2 && mouseX <= width / 2 + textWidth / 2 + 2) {
                    return line.dep();
                }
            }
            y += LINE_HEIGHT;
        }
        return null;
    }

    private static Component tooltip(ExternalDependency dep) {
        return dep.location() != null
                ? Component.literal("Found at: " + dep.location())
                : Component.literal("Not found on your system!");
    }

    private record Line(FormattedCharSequence text, ExternalDependency dep) {
    }
}
