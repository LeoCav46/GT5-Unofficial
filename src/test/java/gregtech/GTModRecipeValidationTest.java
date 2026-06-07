package gregtech;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GTModRecipeValidationTest {

    @Test
    void validationRunnerThrowsCleanRunWhenNoValidatorsAreEnabled() {
        assertThrows(
            IllegalStateException.class,
            () -> GTMod.runRecipeValidationsForTesting(false, false, () -> {}, () -> {}));
    }

    @Test
    void validationRunnerRunsBothValidatorsBeforeThrowingCombinedFailure() {
        boolean[] ranLookup = { false };
        boolean[] ranDuplicates = { false };

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> GTMod.runRecipeValidationsForTesting(
                true,
                true,
                () -> {
                    ranLookup[0] = true;
                    throw new IllegalStateException("lookup failure");
                },
                () -> {
                    ranDuplicates[0] = true;
                    throw new IllegalStateException("duplicate failure");
                }));

        assertTrue(ranLookup[0]);
        assertTrue(ranDuplicates[0]);
        assertTrue(error.getMessage().contains("lookup failure"));
        assertTrue(error.getMessage().contains("duplicate failure"));
    }

    @Test
    void validationRunnerCanRunOnlyDuplicateValidator() {
        boolean[] ranDuplicates = { false };

        assertThrows(
            IllegalStateException.class,
            () -> GTMod.runRecipeValidationsForTesting(
                false,
                true,
                () -> {
                    throw new AssertionError("lookup should not run");
                },
                () -> ranDuplicates[0] = true));

        assertTrue(ranDuplicates[0]);
    }

    @Test
    void validationRunnerRunsOnlyLookupValidatorWhenOnlyLookupFlagIsEnabled() {
        boolean[] ranLookup = { false };

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> GTMod.runRecipeValidationsForTesting(
                true,
                false,
                () -> ranLookup[0] = true,
                () -> {
                    throw new AssertionError("duplicate should not run");
                }));

        assertTrue(ranLookup[0]);
        assertTrue(error.getMessage().contains("GT recipe validation found 0 issue(s); run completed."));
    }

    @Test
    void validationRunnerCleanMessageCoversBothValidators() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> GTMod.runRecipeValidationsForTesting(true, true, () -> {}, () -> {}));

        assertTrue(error.getMessage().contains("GT recipe validation found 0 issue(s); run completed."));
    }
}
