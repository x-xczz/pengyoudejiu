const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');

test('示例配置、苹果糖模型和贴图已移除', () => {
  for (const file of [
    'src/main/java/com/example/examplemod/Config.java',
    'src/main/resources/assets/examplemod/models/item/example_item.json',
    'src/main/resources/assets/examplemod/textures/item/example_item.png'
  ]) assert.equal(fs.existsSync(path.join(root, file)), false, file);
});

test('共享翻译移除示例条目，保留唱片及音乐字幕', () => {
  for (const locale of ['zh_cn', 'en_us']) {
    const data = JSON.parse(read(`src/main/resources/assets/examplemod/lang/${locale}.json`));
    assert.equal(data['item.examplemod.example_item'], undefined);
    assert.equal(data['block.examplemod.example_block'], undefined);
    assert.equal(data['item.examplemod.dance_disc.desc'], '小助 — 朋友的酒');
    assert.ok(data['subtitles.examplemod.dance_song']);
  }
});

test('清理入口仍接入唱片、网络、原服务端配置和原版创造分页', () => {
  const entry = read('src/main/java/com/example/examplemod/ExampleMod.java');
  for (const text of ['ModMusic.register(modEventBus)', 'DanceNetwork.register()',
    'DanceConfig.SPEC, "examplemod-dance-server.toml"', 'CreativeModeTabs.TOOLS_AND_UTILITIES',
    'event.accept(ModMusic.DANCE_DISC)', '"example_tab"']) assert.ok(entry.includes(text), text);
  assert.doesNotMatch(entry, /EXAMPLE_ITEM|EXAMPLE_BLOCK|(?<!Dance)Config\.SPEC|FoodProperties|HELLO FROM|MINECRAFT NAME/);
});
