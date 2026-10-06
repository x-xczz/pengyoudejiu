'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {oggCrc, readVorbisMetadata, createManifest} = require('../tools/import-music.cjs');

// 合成容器头仅用于元数据解析测试，不将它当作可播放音频或用户的音乐。
function fixture({channels = 1, magic = 'vorbis', serial = 1} = {}) {
  const header = Buffer.alloc(30); header[0] = 1; header.write(magic, 1); header[11] = channels;
  header.writeUInt32LE(48000, 12); header[28] = 0xb8; header[29] = 1;
  function page(body, flags, sequence, granule) {
    const output = Buffer.alloc(28 + body.length); output.write('OggS'); output[5] = flags;
    output.writeBigInt64LE(BigInt(granule), 6); output.writeUInt32LE(serial, 14);
    output.writeUInt32LE(sequence, 18); output[26] = 1; output[27] = body.length;
    body.copy(output, 28); output.writeUInt32LE(oggCrc(output), 22); return output;
  }
  return Buffer.concat([page(header, 2, 0, 0), page(Buffer.from([0]), 4, 1, 120000)]);
}
test('读取单声道 Vorbis 的准确采样时长', () => {
  assert.deepEqual(readVorbisMetadata(fixture()), {durationSeconds: 2.5, sampleRate: 48000, channels: 1});
});
test('生成元数据并保留曲名、作者、节奏', () => {
  const value = createManifest(fixture(), {title:'我的曲子', artist:'我', bpm:'135'});
  assert.equal(value.imported, true); assert.equal(value.bpm, 135); assert.equal(value.durationSeconds, 2.5);
  assert.equal(value.title, '我的曲子'); assert.equal(value.artist, '我');
});
test('拒绝只有 OGG 后缀但不是 Vorbis 的文件', () => {
  assert.throws(() => readVorbisMetadata(Buffer.from('not audio')), /OGG/);
  assert.throws(() => readVorbisMetadata(fixture({magic:'OpusHd'})), /Vorbis/);
});
test('拒绝立体声，防止唱片机距离衰减异常', () => {
  assert.throws(() => readVorbisMetadata(fixture({channels:2})), /单声道/);
});
test('拒绝损坏、截断和串接的文件', () => {
  const bad = fixture(); bad[40] ^= 1;
  assert.throws(() => readVorbisMetadata(bad), /CRC/);
  assert.throws(() => readVorbisMetadata(fixture().subarray(0, 65)), /截断|完整/);
  assert.throws(() => readVorbisMetadata(Buffer.concat([fixture(), fixture({serial:2})])), /串接/);
});
test('拒绝不合法 BPM', () => {
  for (const bpm of [0, 301, NaN, Infinity]) assert.throws(() => createManifest(fixture(), {bpm}), /BPM/);
});

const fs = require('node:fs');
const path = require('node:path');
const {importMusic} = require('../tools/import-music.cjs');
function importFixture() {
  // 测试素材只写 build，不污染真实唱片，也不删除用户文件。
  const build = path.resolve(__dirname, '../build');
  fs.mkdirSync(build, {recursive:true});
  const root = fs.mkdtempSync(path.join(build, 'music-import-test-'));
  const assets = path.join(root, 'assets/examplemod');
  fs.mkdirSync(path.join(assets, 'lang'), {recursive:true});
  fs.writeFileSync(path.join(assets, 'sounds.json'), JSON.stringify({existing:{sounds:[]}}));
  for (const lang of ['zh_cn', 'en_us']) {
    fs.writeFileSync(path.join(assets, 'lang', lang + '.json'), JSON.stringify({'item.examplemod.example_item':'保留食物'}));
  }
  return {root, assets};
}
test('导入后所有 JSON 可解析，保留原文件、其他声音与翻译', () => {
  const {root, assets} = importFixture();
  const source = path.join(root, 'original.ogg');
  const data = fixture(); fs.writeFileSync(source, data);
  importMusic(source, {title:'朋友的酒', artist:'测试作者', bpm:110}, root);
  const manifest = JSON.parse(fs.readFileSync(path.join(root, 'dance_track.json'), 'utf8'));
  assert.equal(manifest.imported, true); assert.equal(manifest.durationSeconds, 2.5);
  assert.equal(manifest.bpm, 110);
  assert.deepEqual(fs.readFileSync(source), data);
  assert.deepEqual(fs.readFileSync(path.join(assets, 'sounds/records/dance_song.ogg')), data);
  const sounds = JSON.parse(fs.readFileSync(path.join(assets, 'sounds.json'), 'utf8'));
  assert.deepEqual(sounds.existing, {sounds:[]});
  assert.equal(sounds['music_disc.dance_song'].sounds[0].stream, true);
  assert.equal(sounds['music_disc.dance_song'].sounds[0].attenuation_distance, 16);
  for (const lang of ['zh_cn', 'en_us']) {
    const messages = JSON.parse(fs.readFileSync(path.join(assets, 'lang', lang + '.json'), 'utf8'));
    assert.equal(messages['item.examplemod.example_item'], '保留食物');
    assert.equal(messages['item.examplemod.dance_disc.desc'], '测试作者 — 朋友的酒');
  }
});
test('输入校验失败时不改写资源', () => {
  const {root, assets} = importFixture();
  const source = path.join(root, 'invalid.ogg'); fs.writeFileSync(source, 'invalid');
  const soundFile = path.join(assets, 'sounds.json'), before = fs.readFileSync(soundFile);
  assert.throws(() => importMusic(source, {}, root), /OGG/);
  assert.deepEqual(fs.readFileSync(soundFile), before);
  assert.equal(fs.existsSync(path.join(root, 'dance_track.json')), false);
});

// 同时检查实际打包的源资源，避免只更新元数据却忘记播放提示或声音衰减配置。
test('当前音乐署名在两种语言中一致，保留原版声音衰减', () => {
  const resources = path.resolve(__dirname, '../src/main/resources');
  const track = JSON.parse(fs.readFileSync(path.join(resources, 'dance_track.json'), 'utf8'));
  assert.equal(track.artist, '小助');
  for (const locale of ['zh_cn', 'en_us']) {
    const lang = JSON.parse(fs.readFileSync(path.join(resources, 'assets/examplemod/lang', locale + '.json'), 'utf8'));
    assert.equal(lang['item.examplemod.dance_disc.desc'], track.artist + ' — ' + track.title);
  }
  const sounds = JSON.parse(fs.readFileSync(path.join(resources, 'assets/examplemod/sounds.json'), 'utf8'));
  const sound = sounds['music_disc.dance_song'].sounds[0];
  assert.equal(sound.stream, true);
  assert.equal(sound.attenuation_distance, 16);
  assert.equal(sound.volume ?? 1, 1);
  assert.equal(4 * sound.attenuation_distance, 64);
});
