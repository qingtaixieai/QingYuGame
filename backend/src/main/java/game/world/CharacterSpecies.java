package game.world;

import java.util.List;

final class CharacterSpecies {
    record Definition(String code, String name, String description) {}

    static final Definition BULLY = new Definition("bully", "恶霸", "近战怪物，会主动接近旅人。");
    static final List<Definition> ALL = List.of(
        BULLY,
        new Definition("claw", "利爪怪", "擅长近身攻击的怪物。"),
        new Definition("archer", "弓箭手", "会使用远程武器的怪物。")
    );

    static Definition require(String code) {
        return ALL.stream().filter(entry -> entry.code().equals(code)).findFirst()
            .orElseThrow(() -> AccountService.bad("未知的生物种类"));
    }

    private CharacterSpecies() {}
}
