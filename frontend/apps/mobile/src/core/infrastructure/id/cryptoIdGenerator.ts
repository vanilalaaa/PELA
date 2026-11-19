import * as Crypto from 'expo-crypto';
import { createIdGenerator } from './idGenerator';

/** IdGenerator trên expo-crypto (native). Import riêng để test không kéo native module vào. */
export function createCryptoIdGenerator() {
  return createIdGenerator(() => Crypto.randomUUID());
}
