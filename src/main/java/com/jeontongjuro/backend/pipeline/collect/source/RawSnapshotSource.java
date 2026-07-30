package com.jeontongjuro.backend.pipeline.collect.source;

import com.jeontongjuro.backend.pipeline.collect.RawDataset;

/**
 * 수집 원천 추상화. 라이브 odcloud 호출(OdcloudRawSnapshotSource)과
 * 골든 픽스처 주입(테스트의 FixtureRawSnapshotSource)을 같은 계약으로 바꿔 끼운다.
 * 구현체는 RawSnapshot의 순서 계약(원본 totalCount 순서 전역 병합)을 반드시 지켜야 한다.
 */
public interface RawSnapshotSource {

    RawSnapshot fetch(RawDataset dataset);
}
