/* BranchIA64.c */

#include "BranchIA64.h"

#ifdef _WINDOWS
typedef unsigned __int64 UInt64;
#endif

const Byte kBranchTable[32] = 
{ 
  0, 0, 0, 0, 0, 0, 0, 0,
  0, 0, 0, 0, 0, 0, 0, 0,
  4, 4, 6, 6, 0, 0, 7, 7,
  4, 4, 0, 0, 4, 4, 0, 0 
};

UInt32 IA64_Convert(Byte *data, UInt32 size, UInt32 nowPos, int encoding)
{
  UInt32 i;
  for (i = 0; i + 16 <= size; i += 16)
  {
    UInt32 instrTemplate = data[i] & 0x1F;
    UInt32 mask = kBranchTable[instrTemplate];
    UInt32 bitPos = 5;
    int slot;
    for (slot = 0; slot < 3; slot++, bitPos += 41)
    {
      UInt32 bytePos;
      UInt32 bitRes;
      UInt64 instruction;
      UInt64 instNorm;
      int j;
      if (((mask >> slot) & 1) == 0)
        continue;
      bytePos = (bitPos >> 3);
      bitRes = bitPos & 0x7;
      instruction = 0;
      for (j = 0; j < 6; j++)
        instruction += (UInt64)(data[i + j + bytePos]) << (8 * j);

      instNorm = instruction >> bitRes;
      if (((instNorm >> 37) & 0xF) == 0x5 
        &&  ((instNorm >> 9) & 0x7) == 0 
        /* &&  (instNorm & 0x3F)== 0 */
        )
      {
        UInt32 dest;
        UInt32 src = (UInt32)((instNorm >> 13) & 0xFFFFF);
        src |= ((instNorm >> 36) & 1) << 20;
        
        src <<= 4;
        
        if (encoding)
          dest = nowPos + i + src;
        else
          dest = src - (nowPos + i);
        
        dest >>= 4;
        
        instNorm &= ~(((UInt64)(0x8FFFFF)) << 13);
        instNorm |= (((UInt64)(dest & 0xFFFFF)) << 13);
        instNorm |= (((UInt64)(dest & 0x100000)) << (36 - 20));
        
        instruction &= (1 << bitRes) - 1;
        instruction |= (instNorm << bitRes);
        for (j = 0; j < 6; j++)
          data[i + j + bytePos] = (Byte)(instruction >> (8 * j));
      }
    }
  }
  return i;
}
