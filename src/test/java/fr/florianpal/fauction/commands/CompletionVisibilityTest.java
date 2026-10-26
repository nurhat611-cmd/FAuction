package fr.florianpal.fauction.commands;

import fr.florianpal.fauction.FAuctionTestBase;
import fr.florianpal.fauction.configurations.CommandsConfig;
import fr.florianpal.fauction.configurations.TestConfigs;
import org.bukkit.entity.Player;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandNode;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.permission.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@code list} and {@code expire} leave the completion of ordinary players, in the real command
 * tree Cloud builds from the annotations : what changes is the permission of the words'
 * <em>visibility</em> metadata, never the permission of the commands themselves — both stay
 * runnable by everyone who could run them before.
 */
class CompletionVisibilityTest extends FAuctionTestBase {

    private static final Logger LOGGER = Logger.getLogger("CompletionVisibilityTest");

    private static final class HeadlessManager extends CommandManager<Object> {

        private HeadlessManager() {
            super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());
        }

        @Override
        public boolean hasPermission(Object sender, String permission) {
            return true;
        }
    }

    @BeforeEach
    void setUpCommand() {
        when(globalConfig.getDecimalFormat()).thenReturn("0.00");
    }

    @Test
    @DisplayName("Only list and expire stop being proposed to ordinary players")
    void listAndExpireLeaveTheCompletion() {

        CommandNode<Object> root = root(register());

        // "Contains" : Cloud merges the permissions propagated to a node with anyOf, whose
        // spelling parenthesises — (fauction.list) — what matters is the permission inside.
        assertTrue(completionPermission(root, "list").contains("fauction.list"));
        assertTrue(completionPermission(root, "expire").contains("fauction.expire"));

        fr.florianpal.fauction.managers.commandmanagers.CommandManager.hideFromCompletion(
                root, Set.of("list", "expire"), Permission.of("fauction.tabcomplete"));

        assertEquals("fauction.tabcomplete", completionPermission(root, "list"));
        assertEquals("fauction.tabcomplete", completionPermission(root, "expire"));

        // The trading words keep being proposed to whoever may trade.
        assertTrue(completionPermission(root, "sell").contains("fauction.sell"));
        assertTrue(completionPermission(root, "search").contains("fauction.search"));
        assertTrue(completionPermission(root, "lookup").contains("fauction.lookup"));
    }

    private CommandManager<Object> register() {
        CommandsConfig commandsConfig = new CommandsConfig();
        commandsConfig.load(TestConfigs.of("lang: \"en\"\n"), LOGGER);

        HeadlessManager manager = new HeadlessManager();
        AnnotationParser<Object> parser = new AnnotationParser<>(manager, Object.class);
        parser.stringProcessor(new CommandPlaceholders(commandsConfig));
        parser.parse(new AuctionCommand(plugin));
        return manager;
    }

    private static CommandNode<Object> root(CommandManager<Object> manager) {
        CommandNode<Object> root = manager.commandTree().getNamedNode("ah");
        assertNotNull(root, "the ah node");
        return root;
    }

    /**
     * The permission a player needs for the word to be proposed to them — the access metadata
     * Brigadier turns into the requirement of the literal sent to the client.
     */
    private static String completionPermission(CommandNode<Object> root, String literal) {
        for (CommandNode<Object> child : root.children()) {
            if (child.component() == null || !child.component().name().equals(literal)) {
                continue;
            }
            Map<Type, Permission> access = child.nodeMeta().getOrNull(CommandNode.META_KEY_ACCESS);
            assertNotNull(access, literal);
            Permission permission = access.get(Player.class);
            assertNotNull(permission, literal);
            return permission.permissionString();
        }
        throw new AssertionError("No literal " + literal + " under /ah");
    }
}
