'use strict';
const fs = require('node:fs');
const path = require('node:path');

// OGG 使用非反射 CRC-32；校验每页，避免把损坏或仅改后缀的文件导入游戏。
function oggCrc(buffer) {
  let crc = 0;
  for (const value of buffer) {
    crc = (crc ^ (value << 24)) >>> 0;
    for (let bit = 0; bit < 8; bit++) crc = ((crc << 1) ^ ((crc & 0x80000000) ? 0x04c11db7 : 0)) >>> 0;
  }
  return crc;
}

function readVorbisMetadata(buffer) {
  let offset = 0, serial, sequence = 0, firstPacket, lastGranule = -1n, ended = false;
  let fragments = [];
  while (offset < buffer.length) {
    if (offset + 27 > buffer.length || buffer.toString('ascii', offset, offset + 4) !== 'OggS') {
      throw new Error('不是完整的 OGG 文件；不能仅通过修改文件后缀来转换音频。');
    }
    if (buffer[offset + 4] !== 0 || ended) throw new Error('不支持此 OGG 版本或串接音轨。');
    const flags = buffer[offset + 5];
    const currentSerial = buffer.readUInt32LE(offset + 14);
    if (serial === undefined) {
      serial = currentSerial;
      if (!(flags & 2) || (flags & 1)) throw new Error('OGG 缺少有效的起始页。');
    } else if (serial !== currentSerial) throw new Error('只支持单音轨 OGG Vorbis，不支持混合流。');
    if (buffer.readUInt32LE(offset + 18) !== sequence++) throw new Error('OGG 页序号不连续。');
    const count = buffer[offset + 26], bodyStart = offset + 27 + count;
    if (bodyStart > buffer.length) throw new Error('OGG 分段表被截断。');
    const lengths = buffer.subarray(offset + 27, bodyStart);
    const pageEnd = bodyStart + lengths.reduce((a, b) => a + b, 0);
    if (pageEnd > buffer.length) throw new Error('OGG 音频页被截断。');
    const page = Buffer.from(buffer.subarray(offset, pageEnd));
    const crc = page.readUInt32LE(22); page.writeUInt32LE(0, 22);
    if (oggCrc(page) !== crc) throw new Error('OGG CRC 校验失败，文件可能损坏。');
    if (!firstPacket) {
      let cursor = bodyStart;
      for (const length of lengths) {
        fragments.push(buffer.subarray(cursor, cursor + length)); cursor += length;
        if (length < 255) { firstPacket = Buffer.concat(fragments); fragments = []; break; }
      }
    }
    const granule = buffer.readBigInt64LE(offset + 6);
    if (granule >= 0) lastGranule = granule;
    ended = (flags & 4) !== 0;
    offset = pageEnd;
  }
  if (!firstPacket || firstPacket.length < 30 || firstPacket[0] !== 1
      || firstPacket.toString('ascii', 1, 7) !== 'vorbis') {
    throw new Error('需要 OGG Vorbis 音频，不支持 OGG Opus、MP3 或 WAV。');
  }
  if (firstPacket.readUInt32LE(7) !== 0 || !(firstPacket[29] & 1)) throw new Error('无效的 Vorbis 识别头。');
  const channels = firstPacket[11], sampleRate = firstPacket.readUInt32LE(12);
  if (channels !== 1) throw new Error('请先转换为单声道 OGG Vorbis，以便唱片机具有正常的距离衰减。');
  if (!ended || sampleRate < 8000 || sampleRate > 192000 || lastGranule <= 0n) {
    throw new Error('音轨缺少有效结尾、采样率或时长。');
  }
  const durationSeconds = Number(lastGranule) / sampleRate;
  if (!Number.isFinite(durationSeconds) || durationSeconds > 86400) throw new Error('音轨时长超出支持范围。');
  return {durationSeconds, sampleRate, channels};
}

function createManifest(buffer, {title = '原创舞曲', artist = '你的名字', bpm = 120} = {}) {
  const audio = readVorbisMetadata(buffer);
  bpm = Number(bpm);
  if (!Number.isFinite(bpm) || bpm < 30 || bpm > 300) throw new Error('BPM 必须在 30 到 300 之间。');
  return {...audio, bpm, imported: true, title, artist};
}

// 可测试的导入入口；命令行默认只写入本项目的资源目录。
function importMusic(input, options = {}, resources = path.resolve(__dirname, '../src/main/resources')) {
  if (fs.statSync(input).size > 128 * 1024 * 1024) throw new Error('请使用不超过 128 MiB 的音频文件。');
  const buffer = fs.readFileSync(input), manifest = createManifest(buffer, options);
  const assetRoot = path.join(resources, 'assets/examplemod');
  const soundFile = path.join(assetRoot, 'sounds.json');
  const sounds = JSON.parse(fs.readFileSync(soundFile, 'utf8'));
  sounds['music_disc.dance_song'] = {
    subtitle: 'subtitles.examplemod.dance_song',
    // 保持原版 16 格衰减基距；吸引半径单独固定为 32 格，不改变音乐的传播距离。
    sounds: [{name: 'examplemod:records/dance_song', stream: true, attenuation_distance: 16}]
  };
  // 先完成全部读取与校验，再写入；保留其他声音和已有翻译。
  const updates = new Map([
    [path.join(assetRoot, 'sounds/records/dance_song.ogg'), buffer],
    [path.join(resources, 'dance_track.json'), JSON.stringify(manifest, null, 2) + '\n'],
    [soundFile, JSON.stringify(sounds, null, 2) + '\n']
  ]);
  for (const locale of ['zh_cn', 'en_us']) {
    const file = path.join(assetRoot, 'lang', locale + '.json');
    const language = JSON.parse(fs.readFileSync(file, 'utf8'));
    language['item.examplemod.dance_disc.desc'] = manifest.artist + ' — ' + manifest.title;
    updates.set(file, JSON.stringify(language, null, 2) + '\n');
  }
  for (const [file, data] of updates) { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, data); }
  return manifest;
}

function main() {
  const args = process.argv.slice(2);
  if (!args.length) throw new Error('用法：node tools/import-music.cjs "音乐.ogg" --title "曲名" --artist "作者" --bpm 120');
  const input = path.resolve(args.shift()), options = {};
  while (args.length) {
    const key = args.shift();
    if (!['--title', '--artist', '--bpm'].includes(key) || !args.length) throw new Error('无效参数：' + key);
    options[key.slice(2)] = args.shift();
  }
  const manifest = importMusic(input, options);
  console.log('已导入：' + manifest.title + ' / ' + manifest.artist);
  console.log('时长：' + manifest.durationSeconds.toFixed(3) + ' 秒；舞蹈 BPM：' + manifest.bpm);
  console.log('原音频文件未改动。请重启开发游戏，使资源和唱片时长同时更新。');
}
if (require.main === module) { try { main(); } catch (error) { console.error(error.message); process.exitCode = 1; } }
module.exports = {oggCrc, readVorbisMetadata, createManifest, importMusic};
