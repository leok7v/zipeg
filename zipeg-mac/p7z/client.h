#ifndef __p7z__client__
#define __p7z__client__

/*
 {23170F69-40C1-278A-0000-00yy00xx0000}
 
 00 IProgress.h
 
 05  IProgress
 
 01 IFolderArchive.h
 
 05  IArchiveFolder
 // 06  IInFolderArchive // old
 07  IFileExtractCallback.h::IFolderArchiveExtractCallback
 0A  IOutFolderArchive
 0B  IFolderArchiveUpdateCallback
 0C  Agent.h::IArchiveFolderInternal
 0D
 0E  IInFolderArchive
 
 03 IStream.h
 
 01  ISequentialInStream
 02  ISequentialOutStream
 03  IInStream
 04  IOutStream
 06  IStreamGetSize
 07  IOutStreamFlush
 
 
 04 ICoder.h
 
 04  ICompressProgressInfo
 05  ICompressCoder
 18  ICompressCoder2
 20  ICompressSetCoderProperties
 21  ICompressSetDecoderProperties //
 22  ICompressSetDecoderProperties2
 23  ICompressWriteCoderProperties
 24  ICompressGetInStreamProcessedSize
 25  ICompressSetCoderMt
 30  ICompressGetSubStreamSize
 31  ICompressSetInStream
 32  ICompressSetOutStream
 33  ICompressSetInStreamSize
 34  ICompressSetOutStreamSize
 35  ICompressSetBufSize
 40  ICompressFilter
 60  ICompressCodecsInfo
 61  ISetCompressCodecsInfo
 80  ICryptoProperties
 88  ICryptoResetSalt
 8C  ICryptoResetInitVector
 90  ICryptoSetPassword
 A0  ICryptoSetCRC
 
 
 05 IPassword.h
 
 10 ICryptoGetTextPassword
 11 ICryptoGetTextPassword2
 
 
 06 IArchive.h
 
 03  ISetProperties
 
 10  IArchiveOpenCallback
 20  IArchiveExtractCallback
 30  IArchiveOpenVolumeCallback
 40  IInArchiveGetStream
 50  IArchiveOpenSetSubArchiveName
 60  IInArchive
 61  IArchiveOpenSeq
 
 80  IArchiveUpdateCallback
 82  IArchiveUpdateCallback2
 A0  IOutArchive
 
 
 
 08 IFolder.h
 
 00 IFolderFolder
 01 IEnumProperties
 02 IFolderGetTypeID
 03 IFolderGetPath
 04 IFolderWasChanged
 05 // IFolderReload
 06 IFolderOperations
 07 IFolderGetSystemIconIndex
 08 IFolderGetItemFullSize
 09 IFolderClone
 0A IFolderSetFlatMode
 0B IFolderOperationsExtractCallback
 0C //
 0D //
 0E IFolderProperties
 0F
 10 IFolderArcProps
 11 IGetFolderArcProps
 
 
 09 IFolder.h :: FOLDER_MANAGER_INTERFACE
 
 00 - 04 // old IFolderManager
 05 IFolderManager
 
 
 // 0A PluginInterface.h
 00 IInitContextMenu
 01 IPluginOptionsCallback
 02 IPluginOptions
 
 
 Handler GUIDs:
 
 {23170F69-40C1-278A-1000-000110xx0000}
 
 01 Zip
 02 BZip2
 03 Rar
 04 Arj
 05 Z
 06 Lzh
 07 7z
 08 Cab
 09 Nsis
 0A lzma
 0B lzma86
 0C xz
 0D ppmd
 
 D2 SquashFS
 D3 CramFS
 D4 APM
 D5 Mslz
 D6 Flv
 D7 Swf
 D8 Swfc
 D9 Ntfs
 DA Fat
 DB Mbr
 DC Vhd
 DD Pe
 DE Elf
 DF Mach-O
 E0 Udf
 E1 Xar
 E2 Mub
 E3 Hfs
 E4 Dmg
 E5 Compound
 E6 Wim
 E7 Iso
 E8 Bkf
 E9 Chm
 EA Split
 EB Rpm
 EC Deb
 ED Cpio
 EE Tar
 EF GZip
 
 {23170F69-40C1-278A-1000-000100030000} CAgentArchiveHandle
 {23170F69-40C1-278A-1000-000100020000} ContextMenu.h::CZipContextMenu
 
 {23170F69-40C1-278B- old codecs clsids
 
 {23170F69-40C1-278D-1000-000100020000} OptionsDialog.h::CLSID_CSevenZipOptions
 
 {23170F69-40C1-2790-id} Codec Decoders
 {23170F69-40C1-2791-id} Codec Encoders
 
 */

int main2(int numArgs, const char *args[]);


#endif
