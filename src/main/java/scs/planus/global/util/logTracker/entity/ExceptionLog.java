package scs.planus.global.util.logTracker.entity;

import lombok.*;
import scs.planus.domain.BaseTimeEntity;

import javax.persistence.*;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExceptionLog extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exception_log_id")
    private Long id;
    @Embedded
    private MetaData metaData;
    @Embedded
    private ExceptionData exceptionData;

    @Builder
    public ExceptionLog(MetaData metaData, ExceptionData exceptionData) {
        this.metaData = metaData;
        this.exceptionData = exceptionData;
    }
}
