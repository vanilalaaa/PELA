import { Model } from '@nozbe/watermelondb';
import { field } from '@nozbe/watermelondb/decorators';

/** Mirror bảng `vocabulary` (V1__init.sql) — id WatermelonDB = vocab_id của Supabase. */
export class Vocabulary extends Model {
  static table = 'vocabulary';

  @field('word') word!: string;
  @field('meaning') meaning!: string;
  @field('part_of_speech') partOfSpeech!: string | null;
  @field('ipa') ipa!: string | null;
  @field('example_sentence') exampleSentence!: string | null;
  @field('example_meaning') exampleMeaning!: string | null;
  @field('audio_url') audioUrl!: string | null;
  @field('image_url') imageUrl!: string | null;
  @field('topic') topic!: string | null;
  @field('cefr_level') cefrLevel!: string | null;
  /** Vẫn giữ nguyên theo schema server hiện tại — xem comment ở schema.ts. */
  @field('difficulty_level') difficultyLevel!: number;
  @field('source') source!: 'curated' | 'writing';
  @field('owner_user_id') ownerUserId!: string | null;
}
