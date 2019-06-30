package net.runelite.client.plugins.chattranslation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.PlayerMenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import org.apache.commons.lang3.ArrayUtils;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import java.awt.event.KeyEvent;

@PluginDescriptor(
		name = "Chat Translator",
		description = "Translates messages from one Language to another.",
		tags = {"translate", "language", "english", "spanish", "dutch", "french"},
		type = PluginType.UTILITY
)
public class ChatTranslationPlugin extends Plugin implements KeyListener {

	public static final String TRANSLATE = "Translate";
	private static final ImmutableList<String> AFTER_OPTIONS = ImmutableList.of("Message", "Add ignore", "Remove friend", "Kick");

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Provider<MenuManager> menuManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ChatTranslationConfig config;

	@Provides
	ChatTranslationConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChatTranslationConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		if (client != null)
		{
			menuManager.get().addPlayerMenuItem(TRANSLATE);
		}
		keyManager.registerKeyListener(this);
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (client != null)
		{
			menuManager.get().removePlayerMenuItem(TRANSLATE);
		}
		keyManager.unregisterKeyListener(this);
	}

	@Subscribe
	public void onPlayerMenuOptionClicked(PlayerMenuOptionClicked event)
	{
		if (event.getMenuOption().equals(TRANSLATE))
		{
			//TODO: Translate selected message.
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		int groupId = WidgetInfo.TO_GROUP(event.getActionParam1());
		String option = event.getOption();

		if (groupId == WidgetInfo.CHATBOX.getGroupId())
		{
			boolean after;

			if (!AFTER_OPTIONS.contains(option))
			{
				return;
			}

			final MenuEntry lookup = new MenuEntry();
			lookup.setOption(TRANSLATE);
			lookup.setTarget(event.getTarget());
			lookup.setType(MenuAction.RUNELITE.getId());
			lookup.setParam0(event.getActionParam0());
			lookup.setParam1(event.getActionParam1());
			lookup.setIdentifier(event.getIdentifier());

			MenuEntry[] newMenu = ObjectArrays.concat(lookup, client.getMenuEntries());
			int menuEntryCount = newMenu.length;
			ArrayUtils.swap(newMenu, menuEntryCount - 1, menuEntryCount - 2);
			client.setMenuEntries(newMenu);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (client.getGameState() != GameState.LOADING && client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		switch (chatMessage.getType())
		{
			case PUBLICCHAT:
			case MODCHAT:
				if (!config.publicChat())
				{
					return;
				}
				break;
			default:
				return;
		}

		String message = chatMessage.getMessage();

		Translator translator = new Translator();

		try {
			//Automatically check language of message and translate to selected language.
			String translation = translator.translate("auto", config.publicTargetLanguage().toString(), message);
			if (translation != null)
			{
				final MessageNode messageNode = chatMessage.getMessageNode();
				messageNode.setRuneLiteFormatMessage(translation);
				chatMessageManager.update(messageNode);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		client.refreshChat();
	}

	@Override
	public void keyPressed(KeyEvent event) {
		if (client.getGameState() != GameState.LOADING && client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		if (!config.playerChat())
		{
			return;
		}

		Widget chatboxParent = client.getWidget(WidgetInfo.CHATBOX_PARENT);

		if (chatboxParent != null && chatboxParent.getOnKeyListener() != null)
		{
				if (event.getKeyCode() == 0xA)
				{
					event.consume();

					Translator translator = new Translator();

					String message = client.getVar(VarClientStr.CHATBOX_TYPED_TEXT);

					try {
						//Automatically check language of message and translate to selected language.
						String translation = translator.translate("auto", config.playerTargetLanguage().toString(), message);

						if (translation != null)
						{
							client.setVar(VarClientStr.CHATBOX_TYPED_TEXT, translation);

							clientThread.invoke(() -> {
								client.runScript(96, 0, translation);
							});

						}

						client.setVar(VarClientStr.CHATBOX_TYPED_TEXT, "");
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
		} else {
			return;
		}
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		// Nothing.
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
		// Nothing.
	}

}
