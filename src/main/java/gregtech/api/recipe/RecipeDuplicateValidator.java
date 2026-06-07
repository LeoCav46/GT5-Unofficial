package gregtech.api.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.recipe.RecipeLookupValidator.RecipeLookupValidationTarget;
import gregtech.api.util.GTRecipe;

public final class RecipeDuplicateValidator {

    public static final String VALIDATE_DUPLICATES_PROPERTY = "gt.recipe.duplicates.validate";

    private final List<RecipeLookupValidationTarget> targets;
    private final List<String> duplicateIssues = new ArrayList<>();
    private final Set<String> reportedDuplicateKeys = new HashSet<>();
    private int skippedDisabledRecipes;
    private int skippedFakeRecipes;
    private int skippedNoInputRecipes;

    private RecipeDuplicateValidator(List<RecipeLookupValidationTarget> targets) {
        this.targets = targets;
    }

    public static boolean shouldValidateDuplicates() {
        return Boolean.getBoolean(VALIDATE_DUPLICATES_PROPERTY);
    }

    public static void validateDuplicates() {
        validateDuplicates(RecipeMap.ALL_RECIPE_MAPS.values());
    }

    public static void validateDuplicates(String mapName, RecipeMapBackend backend) {
        validateDuplicates(Collections.singletonList(new RecipeLookupValidationTarget(mapName, backend)));
    }

    static void validateDuplicates(Collection<? extends RecipeMap<?>> recipeMaps) {
        List<RecipeLookupValidationTarget> targets = new ArrayList<>();
        for (RecipeMap<?> recipeMap : recipeMaps) {
            RecipeMapBackend backend = recipeMap.getBackend();
            if (backend.doesOverwriteFindRecipe()) {
                continue;
            }
            targets.add(new RecipeLookupValidationTarget(recipeMapName(recipeMap), backend));
        }
        validateDuplicates(targets);
    }

    static void validateDuplicates(List<RecipeLookupValidationTarget> targets) {
        List<RecipeLookupValidationTarget> filteredTargets = targets.stream()
            .filter(target -> !target.backend.doesOverwriteFindRecipe())
            .collect(Collectors.toList());
        new RecipeDuplicateValidator(filteredTargets).validate();
    }

    private static String recipeMapName(RecipeMap<?> recipeMap) {
        return recipeMap == null ? "<unbound>" : recipeMap.unlocalizedName;
    }

    private void validate() {
        for (RecipeLookupValidationTarget target : targets) {
            RecipeMapBackend backend = target.backend;
            List<GTRecipe> recipes = new ArrayList<>(backend.allRecipes());
            backend.ensureLookupCurrent();
            for (GTRecipe recipe : recipes) {
                if (!shouldValidateRecipe(recipe)) {
                    continue;
                }
                List<GTRecipe> duplicates = duplicateMatches(backend, recipe);
                if (!duplicates.isEmpty()) {
                    addDuplicateIssue(target, recipe, duplicates);
                }
            }
        }

        if (!duplicateIssues.isEmpty()) {
            throw buildException();
        }
    }

    private boolean shouldValidateRecipe(GTRecipe recipe) {
        if (!recipe.mEnabled) {
            skippedDisabledRecipes++;
            return false;
        }
        if (recipe.mFakeRecipe) {
            skippedFakeRecipes++;
            return false;
        }
        if (!hasInputs(recipe)) {
            skippedNoInputRecipes++;
            return false;
        }
        return true;
    }

    private boolean hasInputs(GTRecipe recipe) {
        if (recipe.mInputs != null) {
            for (ItemStack item : recipe.mInputs) {
                if (item != null) {
                    return true;
                }
            }
        }
        if (recipe.mFluidInputs != null) {
            for (FluidStack fluid : recipe.mFluidInputs) {
                if (fluid != null && fluid.getFluid() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<GTRecipe> duplicateMatches(RecipeMapBackend backend, GTRecipe queryRecipe) {
        return backend
            .matchRecipeStream(queryRecipe.mInputs, queryRecipe.mFluidInputs, null, null, false, true, true)
            .filter(candidate -> candidate != queryRecipe)
            .collect(Collectors.toList());
    }

    private void addDuplicateIssue(RecipeLookupValidationTarget target, GTRecipe queryRecipe, List<GTRecipe> duplicates) {
        List<GTRecipe> duplicateGroup = new ArrayList<>(duplicates);
        duplicateGroup.add(queryRecipe);
        String duplicateKey = duplicateKey(target.mapName, duplicateGroup);
        if (!reportedDuplicateKeys.add(duplicateKey)) {
            return;
        }

        StringBuilder issue = new StringBuilder();
        issue.append("map=")
            .append(target.mapName)
            .append('\n')
            .append("queryRecipe=")
            .append(RecipeLookupValidator.describeRecipeForValidation(queryRecipe));
        issue.append('\n')
            .append("duplicateMatches=")
            .append(RecipeLookupValidator.describeRecipeListForValidation(duplicates));
        issue.append('\n')
            .append("duplicateMatchCount=")
            .append(duplicates.size());
        duplicateIssues.add(issue.toString());
    }

    private String duplicateKey(String mapName, List<GTRecipe> recipes) {
        List<Integer> identities = recipes.stream()
            .map(System::identityHashCode)
            .sorted()
            .collect(Collectors.toList());
        return mapName + ":" + identities;
    }

    private IllegalStateException buildException() {
        StringBuilder message = new StringBuilder();
        message.append("GT recipe duplicate validation found ")
            .append(duplicateIssues.size())
            .append(" duplicate(s) across ")
            .append(targets.size())
            .append(" map(s).")
            .append("\nskipped recipe(s): disabled=")
            .append(skippedDisabledRecipes)
            .append(", fake=")
            .append(skippedFakeRecipes)
            .append(", noInputs=")
            .append(skippedNoInputRecipes);
        for (int i = 0; i < duplicateIssues.size(); i++) {
            message.append("\n\n")
                .append(i + 1)
                .append(") ")
                .append(duplicateIssues.get(i));
        }
        return new IllegalStateException(message.toString());
    }
}
