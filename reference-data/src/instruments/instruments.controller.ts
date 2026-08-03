import { Controller, Get, NotFoundException, Param } from '@nestjs/common';
import { Instrument } from './instrument.model';
import { InstrumentsService } from './instruments.service';

@Controller('instruments')
export class InstrumentsController {
  constructor(private readonly instrumentsService: InstrumentsService) {}

  @Get()
  async findAll(): Promise<Instrument[]> {
    return this.instrumentsService.findAll();
  }

  @Get(':instrumentKey')
  async findByInstrumentKey(@Param('instrumentKey') instrumentKey: string): Promise<Instrument> {
    const instrument = await this.instrumentsService.findByInstrumentKey(instrumentKey);
    if (!instrument) {
      throw new NotFoundException(`Instrument key "${instrumentKey}" not found.`);
    }
    return instrument;
  }
}
