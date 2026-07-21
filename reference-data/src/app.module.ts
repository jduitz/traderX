import { Module } from '@nestjs/common';
import { HealthModule } from './health/health.module';
import { InstrumentsModule } from './instruments/instruments.module';

@Module({
  imports: [InstrumentsModule, HealthModule],
})
export class AppModule {}
